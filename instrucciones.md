# Instrucciones para ejecutar y probar cada componente

Este documento explica, paso a paso, cómo levantar el sistema del Banco XYZ y verificar cada componente: procesos
batch (Parte 1), BFF por canal (Parte 2) y microservicios resilientes (Parte 3).

---

## 0. Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker Desktop / Docker Engine + Compose v2 | 24+ | Ejecutar todo el sistema (no hace falta instalar Java) |
| RAM asignada a Docker | **8 GB** recomendados (mínimo 6 GB) | 14 contenedores |
| `curl` | cualquiera | Pruebas (en Windows usar `curl.exe`) |
| Java 17 + Maven 3.9 | opcional | Compilar y ejecutar las pruebas automatizadas fuera de Docker |

En Docker Desktop: *Settings → Resources → Memory* = 8 GB.

## 1. Levantar el sistema

```bash
git clone https://github.com/EmilyRupay/Banco-xyz-cloud.git
cd Banco-xyz-cloud
cp .env.example .env                 # opcional: cambiar contraseñas
docker compose up --build -d         # la primera vez compila los 12 módulos (5-10 min)
docker compose ps                    # repetir hasta que todos digan (healthy)
```

> Si vienes de la versión anterior del proyecto (Sumativa 3), borra los datos antiguos: `docker compose down -v`.

Orden de arranque automático (por *healthchecks*):
`postgres, kafka, config-server, eureka-server, auth-server → customers, accounts, payments, notification, batch → bff-web, bff-mobile, bff-atm → gateway`.

Verificaciones rápidas:

| Qué | Cómo | Esperado |
|---|---|---|
| Eureka | Abrir <http://localhost:8761> | 11 aplicaciones registradas (GATEWAY, BFF-WEB, BFF-MOBILE, BFF-ATM, ACCOUNTS-SERVICE, PAYMENTS-SERVICE, CUSTOMERS-SERVICE, NOTIFICATION-SERVICE, BATCH-SERVICE…) |
| Config Server | <http://localhost:8888/payments-service/default> | JSON con la configuración del servicio |
| Gateway HTTPS | `curl -k https://localhost:8443/actuator/health` → `{"status":"UP"}` | Certificado autofirmado (por eso `-k`) |

Detener: `docker compose down` (con `-v` también borra las bases de datos).

## 2. Prueba automática de todo el recorrido

```bash
./scripts/demo.sh                    # Linux / macOS / Git Bash
.\scripts\demo.ps1                   # Windows PowerShell
```

El script ejecuta, en orden, todas las pruebas de las secciones 4 a 7 y muestra cada respuesta.

## 3. Obtener tokens (OAuth2 client_credentials)

Los tokens se piden **a través del Gateway HTTPS** (el Gateway enruta `/oauth2/**` al `auth-server`):

```bash
GW=https://localhost:8443
token() { curl -sk -u "$1" -d grant_type=client_credentials -d "scope=$2" $GW/oauth2/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }

WEB=$(token web-client:web-secret       "web bank.read bank.write")
MOB=$(token mobile-client:mobile-secret "mobile bank.read")
ATM=$(token atm-client:atm-secret       "atm bank.read bank.write")
ADM=$(token admin-client:admin-secret   "web mobile atm bank.read bank.write batch.admin")
```

| Cliente | Secreto | Scopes | Duración |
|---|---|---|---|
| `web-client` | `web-secret` | `web bank.read bank.write` | 30 min |
| `mobile-client` | `mobile-secret` | `mobile bank.read` (solo lectura) | 30 min |
| `atm-client` | `atm-secret` | `atm bank.read bank.write` | **5 min** |
| `admin-client` | `admin-secret` | todos + `batch.admin` | 30 min |
| `postman-client` | `postman-secret` | authorization_code (usuarios `ejecutivo/ejecutivo123`, `cliente/cliente123`) | 30 min |

Con Postman: *Authorization → OAuth 2.0 → Grant type Client Credentials*, Access Token URL
`https://localhost:8443/oauth2/token` (desactivar *SSL certificate verification* en Settings).

En PowerShell: `$WEB = (curl.exe -sk -u web-client:web-secret -d grant_type=client_credentials -d "scope=web bank.read bank.write" https://localhost:8443/oauth2/token | ConvertFrom-Json).access_token`

---

## 4. Parte 1 – Procesos batch (`batch-service`)

Al iniciar, el contenedor ejecuta automáticamente los 3 jobs (`BATCH_RUN_ON_STARTUP=true`). Ver el resultado:

```bash
docker compose logs batch-service | grep -E "Inicia|Fin|step="
```

Se verá, por cada job, la duración y por step: leídos, escritos, omitidos, commits y rollbacks.

### 4.1 Consultar ejecuciones y resultados

```bash
curl -sk -H "Authorization: Bearer $ADM" $GW/api/batch/jobs
curl -sk -H "Authorization: Bearer $ADM" $GW/api/batch/jobs/calculoInteresesMensualesJob/executions
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/resumen-diario?fechaProceso=$(date +%F)"
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/intereses?periodo=2024-12&cuentaId=101"
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/estados-cuenta?anio=2024&cuentaId=101"
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/batch/reports/rechazos?job=DIARIO&clave=$(date +%F)"
```

Los reportes CSV quedan en el volumen `batch-output`:

```bash
docker compose exec batch-service ls -l /app/batch-output
docker compose exec batch-service head /app/batch-output/intereses_mensuales_2024-12.csv
docker compose cp batch-service:/app/batch-output ./batch-output     # copiarlos al equipo
```

### 4.2 Lanzar un job manualmente

| Job | Parámetro identificador | Ejemplo |
|---|---|---|
| `reporteTransaccionesDiariasJob` | `fechaProceso=aaaa-mm-dd` | `fechaProceso=2024-12-31` |
| `calculoInteresesMensualesJob` | `periodo=aaaa-mm` | `periodo=2024-11` |
| `estadosCuentaAnualesJob` | `anio=aaaa` | `anio=2024` |

Parámetros opcionales: `inputDir` (ej. `classpath:legacy-data/semana_1`), `forzar=true` (reprocesar algo que ya
terminó COMPLETED).

```bash
curl -sk -X POST -H "Authorization: Bearer $ADM" \
  "$GW/api/batch/jobs/calculoInteresesMensualesJob/executions?periodo=2024-11"
# -> 202 Accepted con el id de ejecución. Consultar el estado:
curl -sk -H "Authorization: Bearer $ADM" $GW/api/batch/executions/<id>
```

Sin el scope `batch.admin` (por ejemplo con `$WEB`) la respuesta es **403**.

### 4.3 Probar reintentos ante fallos temporales

Simula 2 caídas momentáneas de la base de datos en la línea 10; el *Retry* las reintenta y el job termina COMPLETED:

```bash
curl -sk -X POST -H "Authorization: Bearer $ADM" \
 "$GW/api/batch/jobs/calculoInteresesMensualesJob/executions?periodo=2024-10&simularFalloTransitorioEnLinea=10"
docker compose logs batch-service | grep SIMULACION        # "Fallo transitorio #1 ... #2"
```

En `/api/batch/executions/<id>` el step `cargarCuentasStep` muestra `rollbacks > 0` y estado `COMPLETED`.

### 4.4 Probar la reejecución automática ante un fallo crítico

```bash
curl -sk -X POST -H "Authorization: Bearer $ADM" \
 "$GW/api/batch/jobs/reporteTransaccionesDiariasJob/executions?fechaProceso=2024-12-31&simularFalloCriticoEnLinea=450&forzar=true"
docker compose logs -f batch-service
```

Secuencia esperada en el log: `Fallo CRITICO en linea 450` → `estado=FAILED` → `Reinicio automatico 1/2 en 20 s` →
`Reiniciando reporteTransaccionesDiariasJob` → `estado=COMPLETED`. El reinicio **continúa desde el último commit**
(no reprocesa lo ya guardado) y los totales son los mismos que en una ejecución normal.

Reinicio manual de una ejecución fallida: `POST /api/batch/executions/{id}/restart`.

### 4.5 Verificar la equivalencia con el proceso legacy

```bash
cd batch-service/legacy-reference
D=../src/main/resources/legacy-data/semana_3
./reporte_diario_legacy.sh $D/movimientos_financieros_diarios.csv
./intereses_mensuales_legacy.sh $D/intereses_trimestrales.csv 2>/dev/null
./estados_anuales_legacy.sh $D/estados_financieros_anuales.csv 2024 | grep -v NETO
```

Comparar con los reportes del batch (sección 4.1). Valores esperados:

| | Legacy (Shell/awk) | Spring Batch |
|---|---|---|
| Diario: válidos / alertas / días | 392 / 65 / 239 | 392 / 65 / 239 |
| Diario: créditos / débitos | 244.500 / 284.900 | 244.500 / 284.900 |
| Intereses: cuentas / duplicados | 50 / 347 | 50 / 347 |
| Intereses: ahorro / hipoteca / préstamo | 285,00 / 437,50 / 2.505,00 | 285,00 / 437,50 / 2.505,00 |
| Anual: válidos / cuentas | 604 / 20 | 604 / 20 |
| Anual: depósitos / retiros / compras / pagos | 412.500 / 305.000 / 394.000 / 77.500 | igual |

La misma comparación se ejecuta automáticamente en la prueba `EquivalenciaLegacyIntegrationTest` (sección 8).

---

## 5. Parte 2 – Backend for Frontend

### 5.1 Seguridad por canal

```bash
curl -sk -o /dev/null -w "%{http_code}\n" $GW/web/accounts/101/summary                                   # 401 sin token
curl -sk -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $WEB" $GW/mobile/accounts/101/summary # 403 token web en canal móvil
curl -sk -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $MOB" $GW/atm/accounts/101/balance    # 403 token móvil en ATM
```

### 5.2 BFF Web – respuestas completas

```bash
curl -sk -H "Authorization: Bearer $WEB" $GW/web/customers/1/dashboard     # perfil + cuentas + 10 últimos movimientos c/u
curl -sk -H "Authorization: Bearer $WEB" $GW/web/accounts/101/summary      # cuenta + historial completo + totales
curl -sk -X POST $GW/web/transfers -H "Authorization: Bearer $WEB" -H "Content-Type: application/json" \
     -d '{"cuentaOrigen":101,"cuentaDestino":102,"monto":1000,"descripcion":"Prueba"}'
```

### 5.3 BFF Móvil – respuestas ligeras

```bash
curl -sk -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary   # titular, saldo, 5 movimientos (fecha, tipo, monto)
curl -sk -H "Authorization: Bearer $MOB" $GW/mobile/customers/1/accounts    # [{id, tipo, saldo}]
# Comparar tamaños de respuesta web vs móvil:
curl -sk -o /dev/null -w "web: %{size_download} bytes\n"    -H "Authorization: Bearer $WEB" $GW/web/accounts/101/summary
curl -sk -o /dev/null -w "movil: %{size_download} bytes\n" -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary
# ETag: repetir la petición con If-None-Match -> 304 sin cuerpo
ETAG=$(curl -sk -D - -o /dev/null -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary | grep -i etag | cut -d' ' -f2 | tr -d '\r')
curl -sk -o /dev/null -w "%{http_code}\n" -H "If-None-Match: $ETAG" -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary
```

### 5.4 BFF Cajeros automáticos

```bash
curl -sk -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" $GW/atm/accounts/101/balance        # 200 saldo, cuenta enmascarada
curl -sk -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-999" \
     $GW/atm/accounts/101/balance                                                                    # 403 terminal no registrado
KEY=atm-$(date +%s)
curl -sk -X POST $GW/atm/withdrawals -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" \
     -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":20000}'   # dispensar=true
# Repetir EXACTAMENTE la misma petición: devuelve el mismo resultado y el saldo NO baja de nuevo
curl -sk -X POST $GW/atm/withdrawals -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" \
     -H "Idempotency-Key: $KEY" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":20000}'
# Validaciones: sin Idempotency-Key -> 400 ; monto 12345 -> 400 ; monto mayor al saldo -> 422 dispensar=false
docker compose logs bff-atm | grep AUDITORIA_ATM                                                     # auditoría por terminal
```

---

## 6. Parte 3 – Microservicios

### 6.1 Clientes (`customers-service`)

```bash
curl -sk -H "Authorization: Bearer $ADM" $GW/api/customers
curl -sk -X POST $GW/api/customers -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
  -d '{"rut":"11.111.111-1","nombre":"Maria Perez","email":"maria@correo-ejemplo.cl","telefono":"+56 9 8765 4321","segmento":"PERSONA"}'
curl -sk -X POST $GW/api/customers -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
  -d '{"rut":"11.111.111-2","nombre":"X","email":"x@x.cl","segmento":"PERSONA"}'           # 400 RUT inválido
curl -sk -X PUT $GW/api/customers/8 -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
  -d '{"rut":"11111111-1","nombre":"Maria Perez","email":"nuevo@correo-ejemplo.cl","telefono":"+56 9 8765 4321","segmento":"PREMIUM"}'
#   -> el cambio de email publica una alerta CAMBIO_DATOS_CONTACTO en security.alerts
```

### 6.2 Cuentas (`accounts-service`)

```bash
curl -sk -H "Authorization: Bearer $ADM" $GW/api/accounts/customer/1                       # cuentas del cliente 1
curl -sk -X POST $GW/api/accounts -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
     -d '{"clienteId":8,"tipo":"ahorro","depositoInicial":0}'                               # apertura (valida al titular)
curl -sk -X PUT  $GW/api/accounts/151 -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"tipo":"corriente"}'
curl -sk -X PATCH $GW/api/accounts/151/estado -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
     -d '{"estado":"BLOQUEADA","motivo":"Sospecha de fraude"}'                              # alerta CUENTA_BLOQUEADA
curl -sk -X PATCH $GW/api/accounts/151/estado -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"estado":"ACTIVA"}'
curl -sk -X DELETE $GW/api/accounts/151 -H "Authorization: Bearer $ADM"                     # cierre (solo con saldo 0)
curl -sk -X DELETE $GW/api/accounts/101 -H "Authorization: Bearer $ADM"                     # 409: tiene saldo
```

(El id de la cuenta nueva es el que devuelve la apertura; con los datos iniciales suele ser 151.)

### 6.3 Pagos (`payments-service`)

```bash
curl -sk -X POST $GW/api/payments/deposits    -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":50000}'
curl -sk -X POST $GW/api/payments/withdrawals -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":10000}'
curl -sk -X POST $GW/api/payments/bills       -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":15990,"descripcion":"Luz"}'
curl -sk -X POST $GW/api/payments/transfers   -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaOrigen":101,"cuentaDestino":102,"monto":5000}'
curl -sk -X POST $GW/api/payments/withdrawals -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":99999999}'   # 422 Saldo insuficiente
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/payments/account/101?limite=5"
```

**Transferencia revertida (SAGA con compensación):** bloquear la cuenta destino y transferir:

```bash
curl -sk -X PATCH $GW/api/accounts/102/estado -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"estado":"BLOQUEADA"}'
curl -sk -X POST $GW/api/payments/transfers -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaOrigen":101,"cuentaDestino":102,"monto":5000}'
#   -> estado REVERTIDA: el débito de la 101 se compensó; su saldo no cambia
curl -sk -X PATCH $GW/api/accounts/102/estado -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"estado":"ACTIVA"}'
```

### 6.4 Eventos Kafka (productores y consumidores)

```bash
curl -sk -X POST $GW/api/payments/deposits -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" \
     -d '{"cuentaId":103,"monto":2500000}'                                     # >= $1.000.000 -> alerta MONTO_ALTO
sleep 3
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/notifications?categoria=SEGURIDAD"
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/notifications?categoria=TRANSACCION"
curl -sk -H "Authorization: Bearer $ADM" "$GW/api/notifications?categoria=CUENTA"
docker compose logs notification-service | grep -E "NOTIFICACION ENVIADA|ALERTA DE SEGURIDAD"
# Tópicos y mensajes directamente en Kafka:
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic security.alerts --from-beginning --timeout-ms 5000
# Mensaje inválido -> 3 reintentos -> Dead Letter Topic
docker compose exec kafka sh -c 'echo "{json-invalido" | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic transactions.completed'
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic transactions.completed.DLT --from-beginning --timeout-ms 8000
```

Más de 3 retiros en cajero de la misma cuenta en 10 minutos generan la alerta `RETIROS_FRECUENTES`.

### 6.5 Resiliencia (Resilience4j) y comportamiento alternativo

```bash
docker compose stop accounts-service
for i in 1 2 3 4 5 6; do curl -sk -H "Authorization: Bearer $WEB" $GW/web/accounts/101/summary; echo; done
#   web   -> 200 con "degraded":true y advertencias (fallback)
curl -sk -H "Authorization: Bearer $MOB" $GW/mobile/accounts/101/summary                       # 200 "degradado":true
curl -sk -H "Authorization: Bearer $ATM" -H "X-ATM-ID: ATM-001" $GW/atm/accounts/101/balance  # 503 "Servicio temporalmente no disponible"
curl -sk -X POST $GW/api/payments/deposits -H "Authorization: Bearer $ADM" -H "Content-Type: application/json" -d '{"cuentaId":101,"monto":1000}'
#   -> 503 RECHAZADA "El servicio de cuentas no esta disponible..." (no se pierde dinero)
docker compose exec bff-web wget -qO- --header "Authorization: Bearer $WEB" http://localhost:8084/actuator/circuitbreakers
#   -> "accounts": {"state": "OPEN", ...}
docker compose start accounts-service     # tras ~15 s el circuito pasa a HALF_OPEN y luego CLOSED
```

### 6.6 Balanceo de carga y escalabilidad horizontal

```bash
docker compose up -d --scale payments-service=3 --scale bff-web=2
# Eureka (http://localhost:8761) muestra 3 instancias de PAYMENTS-SERVICE
for i in $(seq 1 9); do curl -sk -o /dev/null -X POST $GW/api/payments/deposits -H "Authorization: Bearer $ADM" \
   -H "Content-Type: application/json" -d '{"cuentaId":104,"monto":1000}'; done
docker compose logs payments-service | grep "COMPLETADA" | cut -d'|' -f1 | sort | uniq -c     # peticiones repartidas
```

### 6.7 Configuración centralizada y descubrimiento

```bash
curl -s http://localhost:8888/bff-atm/default | head -c 600     # configuración servida por Config Server
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | head -c 600
```

---

## 7. Monitoreo

```bash
TRACING_PROBABILITY=1.0 docker compose --profile monitoring up -d
```

| Herramienta | URL | Qué ver |
|---|---|---|
| Grafana | <http://localhost:3000> (admin / admin123) | Dashboard **Banco XYZ – Microservicios**: peticiones/s, latencia p95, errores 5xx, estado de circuit breakers, alertas de seguridad, duración de jobs batch |
| Prometheus | <http://localhost:9090/targets> | Todas las instancias descubiertas vía Eureka |
| Zipkin | <http://localhost:9411> | Traza de una transferencia: gateway → bff-web → payments → accounts → Kafka |
| Logs | `docker compose logs -f <servicio>` | Cada línea incluye `[aplicacion,traceId,spanId]`; el Gateway registra `X-Correlation-Id` |

---

## 8. Pruebas automatizadas (opcional, requiere Java 17 + Maven)

```bash
mvn -B verify                                   # todos los módulos
mvn -pl batch-service -am test                  # pruebas del batch
```

| Prueba | Qué verifica |
|---|---|
| `ReglasDeNegocioTest` | 4 formatos de fecha, tildes, anomalías, duplicados, interés con redondeo comercial, idempotencia ante reintentos |
| `EquivalenciaLegacyIntegrationTest` | Ejecuta los 3 jobs (H2) sobre los datos reales y compara con los resultados legacy; reinicio tras fallo crítico sin duplicar datos; recuperación de fallos transitorios; ejecución particionada |
| `RutTest` | Validación del dígito verificador del RUT |

El *workflow* `.github/workflows/ci.yml` ejecuta lo mismo en cada *push*.

## 9. Solución de problemas

| Síntoma | Causa / solución |
|---|---|
| Un servicio queda `unhealthy` | Esperar 1-2 min; revisar `docker compose logs <servicio>`. Con poca RAM, subir la memoria de Docker a 8 GB |
| `curl: (60) SSL certificate problem` | El certificado es autofirmado: usar `-k` (en navegador, aceptar la advertencia) |
| `401` con un token recién emitido | Si `auth-server` se reinició, las llaves cambian: pedir un token nuevo. El token ATM dura solo 5 min |
| `503` desde el Gateway al inicio | Eureka aún propaga el registro (~30 s). Reintentar |
| `409 Esa ejecucion ya termino COMPLETED` | Agregar `forzar=true` o cambiar el parámetro (fecha/periodo/año) |
| Error de columnas al iniciar accounts | Base de datos de la versión anterior: `docker compose down -v` |
| Puerto 8443/8761/9000/8888 ocupado | Cambiar el mapeo `"host:contenedor"` en `docker-compose.yaml` |
