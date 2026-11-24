package com.banca.seguridad.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Registro de una vulnerabilidad tal como viene en src/test/resources/data/vulnerabilidades.csv.
 */
public record Vulnerabilidad(
    String codigo,
    String tipo,
    String criticidad,
    String descripcion,
    double puntuacionCvss,
    String estado,
    LocalDate fechaDeteccion,
    String metodoDeteccion,
    String impactoPotencial,
    String mitigacionAplicada
) {

    public static final String RECURSO_CSV = "data/vulnerabilidades.csv";
    private static final int COLUMNAS = 10;

    public static Vulnerabilidad desdeLineaCsv(String linea) {
        String[] campos = linea.split(",", -1);
        if (campos.length != COLUMNAS) {
            throw new IllegalArgumentException(
                "Se esperaban " + COLUMNAS + " columnas y llegaron " + campos.length + ": " + linea);
        }
        return new Vulnerabilidad(
            campos[0].trim(),
            campos[1].trim(),
            campos[2].trim(),
            campos[3].trim(),
            Double.parseDouble(campos[4].trim()),
            campos[5].trim(),
            LocalDate.parse(campos[6].trim()),
            campos[7].trim(),
            campos[8].trim(),
            campos[9].trim()
        );
    }

    public static List<Vulnerabilidad> cargarDesdeClasspath() {
        InputStream entrada = Vulnerabilidad.class.getClassLoader().getResourceAsStream(RECURSO_CSV);
        if (entrada == null) {
            throw new IllegalStateException("No se encontro el recurso " + RECURSO_CSV);
        }
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            return lector.lines()
                .skip(1)
                .filter(linea -> !linea.isBlank())
                .map(Vulnerabilidad::desdeLineaCsv)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Error leyendo " + RECURSO_CSV, e);
        }
    }
}
