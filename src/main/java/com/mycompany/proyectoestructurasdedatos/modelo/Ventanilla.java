package com.mycompany.proyectoestructurasdedatos.modelo;

import com.mycompany.proyectoestructurasdedatos.estructuras.ColaPrioridad;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Ventanilla de atención, con un único funcionario y su propia cola de prioridad.
 *
 * El turno en atención sigue dentro de la cola, en el frente y con estado LLAMADO:
 * llamar solo consulta el frente (verFrente) y es al atender o cancelar cuando se
 * extrae (desencolar). Por eso, si la aplicación se cierra a mitad de una atención,
 * el turno no se pierde.
 */
public class Ventanilla {

    private final int id;
    private final String nombreFuncionario;
    private final ColaPrioridad colaTurnos;

    public Ventanilla(int id, String nombreFuncionario) {
        this.id = id;
        this.nombreFuncionario = nombreFuncionario;
        this.colaTurnos = new ColaPrioridad();
    }

    /** Agrega un turno a esta ventanilla, en la posición que le da su prioridad. */
    public void asignarTurno(Turno turno) {
        turno.setIdVentanilla(id);
        colaTurnos.encolarConPrioridad(turno);
    }

    /**
     * Llama al turno del frente y lo pasa a LLAMADO, sin sacarlo de la cola.
     * Si ya había un turno llamado, lo devuelve de nuevo y no se salta a nadie.
     *
     * @return el turno llamado, o null si no hay turnos en la cola
     */
    public Turno llamarSiguiente() {
        Turno turno = colaTurnos.verFrente();
        if (turno != null) {
            turno.setEstado(Turno.LLAMADO);
        }
        return turno;
    }

    /** Finaliza la entrega: extrae el turno llamado de la cola y lo marca ATENDIDO. */
    public void atenderTurno() {
        atenderTurno(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * Igual que atenderTurno(), pero con una hora de atención dada: SistemaEPS la usa
     * para que el turno en memoria quede con la misma hora que guardó en la base de datos.
     */
    public void atenderTurno(LocalDateTime horaAtencion) {
        Turno turno = extraerTurnoLlamado();
        turno.setEstado(Turno.ATENDIDO);
        turno.setHoraAtencion(horaAtencion);
    }

    /** El usuario no se presentó: extrae el turno llamado de la cola y lo marca CANCELADO. */
    public void cancelarTurno() {
        extraerTurnoLlamado().setEstado(Turno.CANCELADO);
    }

    private Turno extraerTurnoLlamado() {
        if (getTurnoEnAtencion() == null) {
            throw new IllegalStateException("La ventanilla " + id + " no tiene un turno llamado.");
        }
        return colaTurnos.desencolar();
    }

    /** Usuarios esperando, sin contar al que ya está en atención. */
    public int getCantidadEnEspera() {
        int total = colaTurnos.tamanio();
        return getTurnoEnAtencion() == null ? total : total - 1;
    }

    /** Usuarios de un tipo que están esperando. Recorre la cola: O(n). */
    public int contarEnEspera(String tipoTurno) {
        int cantidad = 0;
        for (Turno turno : colaTurnos) {
            if (Turno.PENDIENTE.equals(turno.getEstado())
                    && tipoTurno.equals(turno.getUsuario().getTipoTurno())) {
                cantidad++;
            }
        }
        return cantidad;
    }

    /**
     * Busca en la cola el turno de un documento, esté en espera o en atención.
     * Búsqueda lineal: una lista enlazada solo se puede recorrer nodo a nodo, O(n).
     *
     * @return el turno, o null si ese documento no tiene turno en esta ventanilla
     */
    public Turno buscarTurno(String documento) {
        for (Turno turno : colaTurnos) {
            if (documento.equals(turno.getUsuario().getDocumento())) {
                return turno;
            }
        }
        return null;
    }

    /** @return el turno que se está atendiendo, o null si la ventanilla está libre */
    public Turno getTurnoEnAtencion() {
        Turno frente = colaTurnos.verFrente();
        return frente != null && Turno.LLAMADO.equals(frente.getEstado()) ? frente : null;
    }

    public int getId() {
        return id;
    }

    public String getNombreFuncionario() {
        return nombreFuncionario;
    }

    public ColaPrioridad getColaTurnos() {
        return colaTurnos;
    }
}
