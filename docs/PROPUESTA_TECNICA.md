# Propuesta técnica – Banco XYZ en la nube

## 1. Contexto y objetivo
El Banco XYZ mantiene datos legacy (cuentas, intereses y movimientos) con problemas de calidad. Se propone una
plataforma de microservicios **segura, resiliente y desplegable en cualquier entorno cloud** mediante contenedores.

## 2. Decisiones de arquitectura

| Decisión | Alternativas consideradas | Justificación |
|---|---|---|
| **OAuth 2.0 con Spring Authorization Server** (JWT firmado con RSA) | Autenticación Básica por servicio; LDAP | Centraliza la autenticación; los servicios no guardan credenciales; el token viaja firmado y se valida sin consultar al emisor (JWKS). Es el estándar de la industria. |
| **client_credentials** para los frontends y **authorization_code** para usuarios | Password grant | El password grant está eliminado en OAuth 2.1. |
| **Autorización por scopes** (`web`, `mobile`, `bank.read`, `bank.write`) | Roles por usuario | Permite separar canales y permisos de lectura/escritura con mínimo privilegio. |
| **Doble validación del JWT** (Gateway + cada microservicio) | Solo en el Gateway | *Defensa en profundidad*: si alguien accede a un servicio salteando el Gateway, igual es rechazado. |
| **BFF con endpoints personalizados + delegación a microservicios** (estrategias 2 y 3 de la guía S5) | Un backend por canal | Equipo pequeño y requerimiento de dos canales: una sola capa BFF con rutas `/web` y `/mobile` evita duplicar repositorios y permite agregar nuevos canales fácilmente. |
| **Kafka** (en lugar de JMS) | JMS + ActiveMQ | Retención de eventos, particionado por cuenta (orden garantizado) y reprocesamiento; más adecuado para eventos de dominio y escalabilidad. |
| **Resilience4j** en el consumidor (BFF) | Timeouts solamente | Un servicio caído no debe degradar a todo el sistema (ver §4). |
| **Database-per-service** (PostgreSQL con dos bases) | BD compartida | Desacopla los servicios y respeta la autonomía de cada microservicio. |
| **Config Server con perfil `native`** | Repositorio Git | Simplifica la entrega académica; en producción se apunta a Git/S3 cambiando una propiedad. |

## 3. Seguridad
* **Gateway**: rechaza toda petición sin JWT válido (401) o sin el scope del canal (403).
* **Servicios**: cada uno es *Resource Server* y valida la firma contra `http://auth-server:9000/oauth2/jwks`.
* **Propagación del token**: el BFF reenvía el JWT original del usuario a los servicios internos.
* **Mínimo privilegio**: `web-client` y `mobile-client` solo leen; únicamente `admin-client` puede escribir.
* **Contenedores**: ejecutan con usuario no-root; solo se exponen los puertos necesarios.

## 4. Tolerancia a fallos (Resilience4j)
Cada llamada del BFF a un servicio remoto está envuelta, de afuera hacia adentro, por:

1. **Retry**: 3 intentos, 500 ms entre ellos (no reintenta si el circuito está abierto).
2. **Circuit Breaker**: ventana de 10 llamadas, abre si ≥ 50 % fallan (mín. 5 llamadas), espera 15 s y prueba con 3 llamadas en *half-open*.
3. **Bulkhead**: máximo 10 llamadas concurrentes por servicio, para que un servicio lento no agote los hilos.
4. **Fallback**: respuesta alternativa (`degraded: true` + advertencias) en vez de un error 500.
5. **Timeouts** de conexión (2 s) y lectura (3 s) en el cliente HTTP.

Adicionalmente, en Docker: `restart: unless-stopped` (reinicio automático) y *healthchecks*.

## 5. Arquitectura orientada a eventos
`transactions-service` publica `TRANSACTION_CREATED` en el tópico `transactions.created` (3 particiones, clave = `cuentaId`).
`notification-service` lo consume de forma asíncrona.

* **Garantía de entrega**: *at-least-once* (productor con `acks=all` e idempotencia habilitada).
* **Duplicados**: el consumidor es **idempotente** (clave `eventId`).
* **Errores**: 3 reintentos y envío a **Dead Letter Topic** (`transactions.created.DLT`).

## 6. Despliegue
Imágenes construidas con Dockerfile multi-etapa (JDK para compilar → JRE alpine para ejecutar). `docker-compose.yaml`
orquesta 10 contenedores en una red privada, con orden de arranque controlado por *healthchecks*.

## 7. Limitaciones conocidas y mejoras para producción
* Clientes, usuarios y llaves RSA están en memoria → persistir (BD / KMS) y usar secretos externos.
* Escritura dual (BD + Kafka) sin transacción → aplicar el patrón **Transactional Outbox**.
* HTTPS/mTLS entre servicios y un único broker Kafka → TLS, múltiples brokers y réplicas.
* Almacén de notificaciones en memoria → tabla con restricción `UNIQUE(eventId)`.
* Orquestación en Kubernetes / ECS con escalado horizontal y observabilidad (Prometheus, Grafana, trazas).
