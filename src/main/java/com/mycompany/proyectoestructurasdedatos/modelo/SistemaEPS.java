package com.mycompany.proyectoestructurasdedatos.modelo;

import com.mycompany.proyectoestructurasdedatos.estructuras.Cola;
import com.mycompany.proyectoestructurasdedatos.persistencia.TurnoDAO;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Coordinador central del negocio. La interfaz gráfica solo habla con esta clase,
 * que mantiene las 4 ventanillas, genera los códigos y reparte la carga.
 *
 * Toda operación escribe primero en la base de datos y después cambia la memoria.
 * Si MySQL falla, se lanza la excepción antes de tocar las colas, de modo que la
 * memoria y la base de datos nunca quedan en desacuerdo.
 */
public class SistemaEPS {

    public static final int NUM_VENTANILLAS = 4;

    private int contadorGeneral;
    private int contadorPrioridad;
    private int contadorRapido;

    private final Ventanilla[] ventanillas;
    private final TurnoDAO turnoDAO;

    public SistemaEPS() {
        turnoDAO = new TurnoDAO();
        ventanillas = new Ventanilla[] {
            new Ventanilla(1, "Laura Gómez"),
            new Ventanilla(2, "Carlos Ruiz"),
            new Ventanilla(3, "Ana Martínez"),
            new Ventanilla(4, "Jorge Pérez")
        };
    }

    /**
     * Reconstruye el estado al iniciar la aplicación: las colas viven en memoria, de
     * modo que tras un cierre inesperado se vuelven a llenar desde la base de datos,
     * y los contadores continúan donde iban para no repetir códigos.
     *
     * Primero se lee todo y luego se modifica la memoria: si una consulta falla no
     * queda nada a medias y se puede reintentar sin duplicar turnos.
     */
    public void cargarEstadoGuardado() throws SQLException {
        int ultimoGeneral = turnoDAO.obtenerUltimoConsecutivo(Usuario.GENERAL);
        int ultimoPrioridad = turnoDAO.obtenerUltimoConsecutivo(Usuario.PREFERENCIAL);
        int ultimoRapido = turnoDAO.obtenerUltimoConsecutivo(Usuario.RAPIDA);
        Cola<Turno> activos = turnoDAO.consultarTurnosActivos();

        contadorGeneral = ultimoGeneral;
        contadorPrioridad = ultimoPrioridad;
        contadorRapido = ultimoRapido;
        for (Turno turno : activos) {
            getVentanilla(turno.getIdVentanilla()).asignarTurno(turno);
        }
    }

    /**
     * RF01 y RF02: genera el turno del usuario y lo asigna a la ventanilla con menor carga.
     *
     * @return el turno generado, ya guardado en la base de datos y en la cola
     * @throws IllegalStateException si el documento ya tiene un turno sin finalizar
     */
    public Turno registrarUsuario(Usuario usuario) throws SQLException {
        Turno activo = buscarTurnoActivo(usuario.getDocumento());
        if (activo != null) {
            throw new IllegalStateException("El documento " + usuario.getDocumento()
                    + " ya tiene el turno " + activo.getCodigo() + " activo en la ventanilla "
                    + activo.getIdVentanilla() + ".\nDebe atenderse o cancelarse antes de generar otro.");
        }
        String tipo = usuario.getTipoTurno();
        Turno turno = new Turno(generarCodigoTurno(tipo), usuario);
        Ventanilla ventanilla = buscarVentanillaMenorCarga();
        try {
            turnoDAO.insertarTurno(turno, ventanilla.getId());
        } catch (SQLException e) {
            // El turno no llegó a existir: se devuelve el número para no dejar un hueco.
            ajustarContador(tipo, -1);
            throw e;
        }
        ventanilla.asignarTurno(turno);
        return turno;
    }

    /**
     * Turno sin finalizar (en espera o en atención) de un documento. Evita que una
     * misma persona quede con dos turnos a la vez, por ejemplo al registrarla dos veces.
     * Recorre las colas de las 4 ventanillas: O(n) sobre el total de turnos activos.
     *
     * @return el turno, o null si el documento no tiene ninguno activo
     */
    public Turno buscarTurnoActivo(String documento) {
        for (Ventanilla ventanilla : ventanillas) {
            Turno turno = ventanilla.buscarTurno(documento);
            if (turno != null) {
                return turno;
            }
        }
        return null;
    }

    /** Genera el código correlativo según el tipo (ej. G015, P003, R008). */
    public String generarCodigoTurno(String tipo) {
        char letra;
        switch (tipo) {
            case Usuario.PREFERENCIAL:
                letra = 'P';
                break;
            case Usuario.RAPIDA:
                letra = 'R';
                break;
            case Usuario.GENERAL:
                letra = 'G';
                break;
            default:
                throw new IllegalArgumentException("Tipo de turno desconocido: " + tipo);
        }
        return String.format("%c%03d", letra, ajustarContador(tipo, 1));
    }

    private int ajustarContador(String tipo, int cambio) {
        switch (tipo) {
            case Usuario.PREFERENCIAL:
                contadorPrioridad += cambio;
                return contadorPrioridad;
            case Usuario.RAPIDA:
                contadorRapido += cambio;
                return contadorRapido;
            default:
                contadorGeneral += cambio;
                return contadorGeneral;
        }
    }

    /**
     * Balanceo de carga: recorre las 4 ventanillas y elige la que tiene menos usuarios
     * en espera. En caso de empate prefiere una que no esté atendiendo a nadie (si
     * no, un usuario haría fila en una ventanilla ocupada habiendo otra libre) y
     * después la de menor número.
     */
    public Ventanilla buscarVentanillaMenorCarga() {
        Ventanilla mejor = ventanillas[0];
        for (int i = 1; i < ventanillas.length; i++) {
            Ventanilla candidata = ventanillas[i];
            int diferencia = candidata.getCantidadEnEspera() - mejor.getCantidadEnEspera();
            boolean empateYEstaLibre = diferencia == 0
                    && mejor.getTurnoEnAtencion() != null
                    && candidata.getTurnoEnAtencion() == null;
            if (diferencia < 0 || empateYEstaLibre) {
                mejor = candidata;
            }
        }
        return mejor;
    }

    /**
     * RF04: llama al siguiente turno de una ventanilla.
     *
     * @return el turno llamado, o null si no hay nadie esperando
     */
    public Turno llamarSiguiente(int idVentanilla) throws SQLException {
        Ventanilla ventanilla = getVentanilla(idVentanilla);
        if (ventanilla.getTurnoEnAtencion() != null) {
            throw new IllegalStateException("La ventanilla " + idVentanilla
                    + " ya está atendiendo un turno. Debe atenderlo o cancelarlo primero.");
        }
        Turno siguiente = ventanilla.getColaTurnos().verFrente();
        if (siguiente == null) {
            return null;
        }
        turnoDAO.actualizarEstado(siguiente.getCodigo(), Turno.LLAMADO);
        return ventanilla.llamarSiguiente();
    }

    /** RF05: finaliza la entrega del turno en atención y registra la hora de atención. */
    public Turno atenderTurno(int idVentanilla) throws SQLException {
        Ventanilla ventanilla = getVentanilla(idVentanilla);
        Turno turno = exigirTurnoEnAtencion(ventanilla);
        // Una sola hora para la base de datos y para la memoria: si cada una tomara
        // la suya, podrían quedar con un segundo de diferencia.
        LocalDateTime horaAtencion = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        turnoDAO.actualizarEstado(turno.getCodigo(), Turno.ATENDIDO, horaAtencion);
        ventanilla.atenderTurno(horaAtencion);
        return turno;
    }

    /** RF06: cancela el turno en atención porque el usuario no se presentó. */
    public Turno cancelarTurno(int idVentanilla) throws SQLException {
        Ventanilla ventanilla = getVentanilla(idVentanilla);
        Turno turno = exigirTurnoEnAtencion(ventanilla);
        turnoDAO.actualizarEstado(turno.getCodigo(), Turno.CANCELADO);
        ventanilla.cancelarTurno();
        return turno;
    }

    private Turno exigirTurnoEnAtencion(Ventanilla ventanilla) {
        Turno turno = ventanilla.getTurnoEnAtencion();
        if (turno == null) {
            throw new IllegalStateException("La ventanilla " + ventanilla.getId()
                    + " no tiene un turno llamado.");
        }
        return turno;
    }

    /** RF08: consulta de turnos. Cualquier filtro en null se ignora. */
    public Cola<Turno> consultarHistorial(LocalDate fecha, String tipo, Integer idVentanilla,
                                          String estado) throws SQLException {
        return turnoDAO.consultarHistorial(fecha, tipo, idVentanilla, estado);
    }

    /** RF10: estadísticas de atención de un día, o de todos los turnos si la fecha es null. */
    public TurnoDAO.Estadisticas generarEstadisticas(LocalDate fecha) throws SQLException {
        return turnoDAO.generarEstadisticas(fecha);
    }

    /** RF07: las ventanillas con sus colas, para que la interfaz muestre su estado. */
    public Ventanilla[] getEstadoVentanillas() {
        return ventanillas.clone();
    }

    /** Total de usuarios esperando entre las 4 ventanillas. */
    public int getTotalEnEspera() {
        int total = 0;
        for (Ventanilla ventanilla : ventanillas) {
            total += ventanilla.getCantidadEnEspera();
        }
        return total;
    }

    public Ventanilla getVentanilla(int id) {
        if (id < 1 || id > NUM_VENTANILLAS) {
            throw new IllegalArgumentException("Ventanilla inexistente: " + id);
        }
        return ventanillas[id - 1];
    }
}
