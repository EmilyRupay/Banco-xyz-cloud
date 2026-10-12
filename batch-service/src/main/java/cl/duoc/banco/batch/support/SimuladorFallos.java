package cl.duoc.banco.batch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Permite DEMOSTRAR las politicas de tolerancia a fallos sin desconectar la base de datos.
 * Se activa con parametros de job (no identificadores):
 * <ul>
 *   <li>simularFalloTransitorioEnLinea=N : la linea N falla 2 veces con una excepcion transitoria
 *       (como una caida momentanea de la BD). El Retry la reintenta y el job termina COMPLETED.</li>
 *   <li>simularFalloCriticoEnLinea=N : la linea N falla 1 vez con un error no recuperable. El job queda FAILED y
 *       la politica de reejecucion automatica lo reinicia desde el ultimo commit.</li>
 * </ul>
 * Sin esos parametros no hace nada.
 */
@Component
public class SimuladorFallos {

    private static final Logger log = LoggerFactory.getLogger(SimuladorFallos.class);
    private static final int FALLOS_TRANSITORIOS = 2;

    private final Map<String, AtomicInteger> transitorios = new ConcurrentHashMap<>();
    private final Set<String> criticos = ConcurrentHashMap.newKeySet();

    public void verificar(String job, String clave, int linea, String lineaTransitoria, String lineaCritica) {
        if (coincide(lineaTransitoria, linea)) {
            String llave = job + "|" + clave + "|" + linea;
            int intento = transitorios.computeIfAbsent(llave, k -> new AtomicInteger()).incrementAndGet();
            if (intento <= FALLOS_TRANSITORIOS) {
                log.warn("[SIMULACION] Fallo transitorio #{} en linea {} del job {} -> se reintentara",
                        intento, linea, job);
                throw new TransientDataAccessResourceException(
                        "Simulacion: conexion a la base de datos no disponible temporalmente");
            }
        }
        if (coincide(lineaCritica, linea) && criticos.add(job + "|" + clave + "|" + linea)) {
            log.error("[SIMULACION] Fallo CRITICO en linea {} del job {} -> el job quedara FAILED", linea, job);
            throw new IllegalStateException("Simulacion: fallo critico no recuperable en la linea " + linea);
        }
    }

    private static boolean coincide(String parametro, int linea) {
        return parametro != null && !parametro.isBlank() && parametro.trim().equals(String.valueOf(linea));
    }
}
