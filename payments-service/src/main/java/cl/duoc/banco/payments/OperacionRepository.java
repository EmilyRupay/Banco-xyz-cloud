package cl.duoc.banco.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface OperacionRepository extends JpaRepository<Operacion, String> {

    long countByCuentaOrigenAndTipoAndCanalAndEstadoAndCreadaEnAfter(Long cuentaOrigen, Operacion.Tipo tipo,
                                                                     String canal, Operacion.Estado estado,
                                                                     Instant desde);
}
