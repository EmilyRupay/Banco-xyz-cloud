package cl.duoc.banco.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "cuentas")
public class Cuenta {

    @Id
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    private Integer edad;

    @Column(nullable = false)
    private String tipo;

    protected Cuenta() {
        // requerido por JPA
    }

    public Cuenta(Long id, String nombre, BigDecimal saldo, Integer edad, String tipo) {
        this.id = id;
        this.nombre = nombre;
        this.saldo = saldo;
        this.edad = edad;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public BigDecimal getSaldo() { return saldo; }
    public Integer getEdad() { return edad; }
    public String getTipo() { return tipo; }
}
