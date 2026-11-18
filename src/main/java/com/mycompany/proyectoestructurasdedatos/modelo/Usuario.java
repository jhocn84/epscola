package com.mycompany.proyectoestructurasdedatos.modelo;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Usuario de la EPS que llega a reclamar medicamentos. Es el dato base de cada turno.
 */
public class Usuario {

    /** Tipos de turno. Coinciden con los valores del ENUM tipo_turno de la base de datos. */
    public static final String GENERAL = "GENERAL";
    public static final String PREFERENCIAL = "PREFERENCIAL";
    public static final String RAPIDA = "RAPIDA";

    private String nombre;
    private String documento;
    private String tipoTurno;
    private LocalDateTime horaRegistro;

    /** Crea un usuario que se está registrando en este momento en el digiturno. */
    public Usuario(String nombre, String documento, String tipoTurno) {
        this(nombre, documento, tipoTurno, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /** Crea un usuario con una hora de registro conocida (al leerlo de la base de datos). */
    public Usuario(String nombre, String documento, String tipoTurno, LocalDateTime horaRegistro) {
        this.nombre = nombre;
        this.documento = documento;
        this.tipoTurno = tipoTurno;
        this.horaRegistro = horaRegistro;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDocumento() {
        return documento;
    }

    public String getTipoTurno() {
        return tipoTurno;
    }

    public LocalDateTime getHoraRegistro() {
        return horaRegistro;
    }
}
