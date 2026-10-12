package cl.duoc.banco.accounts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    List<Cuenta> findByClienteIdOrderByIdAsc(Long clienteId);

    /** SELECT ... FOR UPDATE: serializa los movimientos concurrentes sobre una misma cuenta (sin sobregiros). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cuenta c WHERE c.id = :id")
    Optional<Cuenta> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(MAX(c.id), 100L) FROM Cuenta c")
    Long maxId();
}
