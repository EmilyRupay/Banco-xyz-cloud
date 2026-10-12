package cl.duoc.banco.customers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank String rut,
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email String email,
        @Pattern(regexp = "^\\+?[0-9 ]{8,15}$", message = "telefono invalido") String telefono,
        @Size(max = 200) String direccion,
        @NotNull Cliente.Segmento segmento) {
}
