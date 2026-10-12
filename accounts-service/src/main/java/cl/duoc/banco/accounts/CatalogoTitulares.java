package cl.duoc.banco.accounts;

import java.util.List;

/** Asocia el nombre del titular legacy con el id de cliente creado por customers-service (mismo orden). */
public final class CatalogoTitulares {

    private static final List<String> TITULARES = List.of("John Doe", "Jane Smith", "Bob Johnson", "Alice Brown",
            "Charlie Green", "Diana Prince", "Steve Rogers");

    private CatalogoTitulares() {
    }

    public static Long clienteId(String nombre) {
        int i = TITULARES.indexOf(nombre);
        return i < 0 ? null : i + 1L;
    }
}
