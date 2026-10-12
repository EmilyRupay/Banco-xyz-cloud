package cl.duoc.banco.batch.domain;

import java.util.ArrayList;
import java.util.List;

/** Separa una linea CSV simple (sin comillas, igual que los archivos legacy) y recorta espacios. */
public final class LectorCsv {

    private LectorCsv() {
    }

    public static List<String> separar(String linea) {
        String limpia = linea.startsWith("﻿") ? linea.substring(1) : linea;
        String[] partes = limpia.split(",", -1);
        List<String> campos = new ArrayList<>(partes.length);
        for (String p : partes) {
            campos.add(p.trim());
        }
        return campos;
    }
}
