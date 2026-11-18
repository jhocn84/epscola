package com.mycompany.proyectoestructurasdedatos.modelo;

import java.time.LocalDateTime;

/**
 * Turno generado para un usuario. Es el elemento que se guarda en las colas.
 *
 * Ciclo de vida:  PENDIENTE -> LLAMADO -> ATENDIDO
 *                                     \-> CANCELADO (el usuario no se presentó)
 */
public class Turno {

    /** Estados posibles. Coinciden con los valores del ENUM estado de la base de datos. */
    public static final String PENDIENTE = "PENDIENTE";
    public static final String LLAMADO = "LLAMADO";
    public static final String ATENDIDO = "ATENDIDO";
    public static final String CANCELADO = "CANCELADO";

    /** Niveles de urgencia: a menor número, mayor prioridad. */
    public static final int PRIORIDAD_PREFERENCIAL = 1;
    public static final int PRIORIDAD_RAPIDA = 2;
    public static final int PRIORIDAD_GENERAL = 3;

    private String codigo;
    private Usuario usuario;
    private int prioridad;
    private String estado;
    private LocalDateTime horaGeneracion;
    private LocalDateTime horaAtencion;
    private int idVentanilla;

    /** Crea un turno nuevo: queda PENDIENTE y su prioridad se deduce del tipo del usuario. */
    public Turno(String codigo, Usuario usuario) {
        this.codigo = codigo;
        this.usuario = usuario;
        this.prioridad = prioridadDe(usuario.getTipoTurno());
        this.estado = PENDIENTE;
        this.horaGeneracion = usuario.getHoraRegistro();
    }

    /** Reconstruye un turno que ya existe en la base de datos. */
    public Turno(String codigo, Usuario usuario, String estado, LocalDateTime horaGeneracion,
                 LocalDateTime horaAtencion, int idVentanilla) {
        this.codigo = codigo;
        this.usuario = usuario;
        this.prioridad = prioridadDe(usuario.getTipoTurno());
        this.estado = estado;
        this.horaGeneracion = horaGeneracion;
        this.horaAtencion = horaAtencion;
        this.idVentanilla = idVentanilla;
    }

    /** Traduce el tipo de turno a su nivel de urgencia. */
    public static int prioridadDe(String tipoTurno) {
        switch (tipoTurno) {
            case Usuario.PREFERENCIAL:
                return PRIORIDAD_PREFERENCIAL;
            case Usuario.RAPIDA:
                return PRIORIDAD_RAPIDA;
            case Usuario.GENERAL:
                return PRIORIDAD_GENERAL;
            default:
                throw new IllegalArgumentException("Tipo de turno desconocido: " + tipoTurno);
        }
    }

    public String getCodigo() {
        return codigo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getHoraGeneracion() {
        return horaGeneracion;
    }

    public LocalDateTime getHoraAtencion() {
        return horaAtencion;
    }

    public void setHoraAtencion(LocalDateTime horaAtencion) {
        this.horaAtencion = horaAtencion;
    }

    public int getIdVentanilla() {
        return idVentanilla;
    }

    public void setIdVentanilla(int idVentanilla) {
        this.idVentanilla = idVentanilla;
    }

    @Override
    public String toString() {
        return codigo + " (" + estado + ")";
    }
}
