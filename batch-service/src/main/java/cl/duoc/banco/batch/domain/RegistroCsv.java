package cl.duoc.banco.batch.domain;

import java.util.List;

/**
 * Linea cruda de un archivo legacy: numero de linea (para trazabilidad), contenido original y campos ya recortados.
 */
public record RegistroCsv(int linea, String contenido, List<String> campos) {

    public String campo(int indice) {
        return indice < campos.size() ? campos.get(indice) : "";
    }
}
