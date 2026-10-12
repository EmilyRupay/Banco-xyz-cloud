package cl.duoc.banco.payments;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId);

    List<Movimiento> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId, Pageable pageable);
}
