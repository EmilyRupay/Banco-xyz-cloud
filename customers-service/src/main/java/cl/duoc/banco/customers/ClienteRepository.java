package cl.duoc.banco.customers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByRut(String rut);

    boolean existsByRut(String rut);

    @Query("SELECT COALESCE(MAX(c.id), 0L) FROM Cliente c")
    Long maxId();
}
