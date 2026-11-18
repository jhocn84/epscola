package com.mycompany.proyectoestructurasdedatos.persistencia;

import com.mycompany.proyectoestructurasdedatos.estructuras.Cola;
import com.mycompany.proyectoestructurasdedatos.modelo.SistemaEPS;
import com.mycompany.proyectoestructurasdedatos.modelo.Turno;
import com.mycompany.proyectoestructurasdedatos.modelo.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Patrón DAO (Data Access Object): todo el SQL del sistema está en esta clase.
 * El resto del programa trabaja con objetos Turno y no sabe qué tablas existen.
 *
 * Se usa siempre PreparedStatement: los valores viajan como parámetros y no se
 * concatenan al SQL, lo que evita la inyección SQL.
 *
 * El try-with-resources cierra sentencias y resultados, pero NO la conexión: esa
 * la administra el Singleton ConexionDB y se reutiliza durante toda la ejecución.
 */
public class TurnoDAO {

    /**
     * Máximo de turnos que devuelve una consulta de historial. Sin este tope, tras
     * meses de uso se cargarían en pantalla decenas de miles de filas cada vez.
     */
    public static final int LIMITE_HISTORIAL = 1000;

    private static final String COLUMNAS =
            "SELECT codigo_turno, nombre_usuario, documento, tipo_turno, id_ventanilla,"
            + " hora_generacion, hora_atencion, estado FROM turnos";

    /**
     * Turnos generados en un día. Se compara contra un rango y no con
     * DATE(hora_generacion) = ?, porque aplicar una función a la columna impediría
     * usar su índice y obligaría a leer la tabla entera.
     */
    private static final String FILTRO_DIA = " AND hora_generacion >= ? AND hora_generacion < ?";

    /** Segundos entre la generación de un turno y su atención. */
    private static final String ESPERA = "TIMESTAMPDIFF(SECOND, hora_generacion, hora_atencion)";

    /** Resultados de generarEstadisticas(). */
    public static class Estadisticas {
        /** Índice = id de ventanilla - 1. */
        public final int[] atendidosPorVentanilla = new int[SistemaEPS.NUM_VENTANILLAS];
        /** En estos arreglos, índice = prioridad - 1 (0 = P, 1 = R, 2 = G). */
        public final int[] atendidosPorTipo = new int[3];
        public final int[] canceladosPorTipo = new int[3];
        public final long[] esperaPromedioSegundos = new long[3];
        public final long[] esperaMaximaSegundos = new long[3];
        public int totalAtendidos;
        public int totalCancelados;
        public int totalActivos;
    }

    /** RF09: guarda un turno recién generado. */
    public void insertarTurno(Turno turno, int idVentanilla) throws SQLException {
        String sql = "INSERT INTO turnos (codigo_turno, nombre_usuario, documento, tipo_turno,"
                + " prioridad, id_ventanilla, hora_generacion, hora_atencion, estado)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conexion = ConexionDB.getInstancia().getConexion();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, turno.getCodigo());
            ps.setString(2, turno.getUsuario().getNombre());
            ps.setString(3, turno.getUsuario().getDocumento());
            ps.setString(4, turno.getUsuario().getTipoTurno());
            ps.setInt(5, turno.getPrioridad());
            ps.setInt(6, idVentanilla);
            ps.setObject(7, turno.getHoraGeneracion());
            ps.setObject(8, turno.getHoraAtencion());
            ps.setString(9, turno.getEstado());
            ps.executeUpdate();
        }
    }

    /**
     * Cambia el estado de un turno. Al pasar a ATENDIDO registra además la hora
     * actual como hora de atención.
     */
    public void actualizarEstado(String codigo, String estado) throws SQLException {
        LocalDateTime horaAtencion = Turno.ATENDIDO.equals(estado)
                ? LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) : null;
        actualizarEstado(codigo, estado, horaAtencion);
    }

    /**
     * Cambia el estado de un turno indicando la hora de atención, que es con la que
     * se calcula el tiempo de espera. Recibirla permite que la base de datos y el
     * turno en memoria guarden exactamente la misma hora.
     *
     * Solo se modifica un turno activo (PENDIENTE o LLAMADO): ATENDIDO y CANCELADO
     * son estados finales, y el WHERE impide reabrirlos o finalizarlos dos veces.
     *
     * @param horaAtencion la hora de la entrega si el estado es ATENDIDO; si no, null
     */
    public void actualizarEstado(String codigo, String estado, LocalDateTime horaAtencion)
            throws SQLException {
        String sql = "UPDATE turnos SET estado = ?, hora_atencion = ?"
                + " WHERE codigo_turno = ? AND estado IN ('PENDIENTE', 'LLAMADO')";
        Connection conexion = ConexionDB.getInstancia().getConexion();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setObject(2, horaAtencion);
            ps.setString(3, codigo);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("El turno " + codigo
                        + " no existe en la base de datos o ya fue finalizado.");
            }
        }
    }

    /** RF08: los turnos registrados, del más reciente al más antiguo. */
    public Cola<Turno> consultarHistorial() throws SQLException {
        return consultarHistorial(null, null, null, null);
    }

    /**
     * RF08: consulta con filtros por fecha, tipo, ventanilla y estado.
     * El WHERE se arma dinámicamente con los filtros que no sean null, pero los valores
     * siguen viajando como parámetros (?), nunca concatenados al SQL.
     *
     * Los resultados se devuelven en nuestra propia Cola<Turno>, ya que el proyecto
     * no usa colecciones nativas de Java. El orden de lectura se conserva por ser FIFO.
     *
     * @return como máximo LIMITE_HISTORIAL turnos, los más recientes
     */
    public Cola<Turno> consultarHistorial(LocalDate fecha, String tipo, Integer idVentanilla,
                                          String estado) throws SQLException {
        StringBuilder sql = new StringBuilder(COLUMNAS).append(" WHERE 1 = 1");
        Object[] parametros = new Object[5];
        int cantidad = 0;
        if (fecha != null) {
            sql.append(FILTRO_DIA);
            parametros[cantidad++] = fecha.atStartOfDay();
            parametros[cantidad++] = fecha.plusDays(1).atStartOfDay();
        }
        if (tipo != null) {
            sql.append(" AND tipo_turno = ?");
            parametros[cantidad++] = tipo;
        }
        if (idVentanilla != null) {
            sql.append(" AND id_ventanilla = ?");
            parametros[cantidad++] = idVentanilla;
        }
        if (estado != null) {
            sql.append(" AND estado = ?");
            parametros[cantidad++] = estado;
        }
        // LIMITE_HISTORIAL es una constante entera del programa, no un dato del usuario.
        sql.append(" ORDER BY hora_generacion DESC, id_turno DESC LIMIT ").append(LIMITE_HISTORIAL);

        Connection conexion = ConexionDB.getInstancia().getConexion();
        try (PreparedStatement ps = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < cantidad; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return leerTurnos(rs);
            }
        }
    }

    /**
     * Turnos sin finalizar, para reconstruir las colas al iniciar la aplicación.
     * Los LLAMADO van primero para que queden en el frente de su ventanilla;
     * el resto, por orden de llegada para respetar el FIFO.
     */
    public Cola<Turno> consultarTurnosActivos() throws SQLException {
        String sql = COLUMNAS + " WHERE estado IN ('PENDIENTE', 'LLAMADO')"
                + " ORDER BY (estado = 'LLAMADO') DESC, id_turno ASC";
        Connection conexion = ConexionDB.getInstancia().getConexion();
        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return leerTurnos(rs);
        }
    }

    /** Mayor número usado en un tipo de turno (15 para G015), o 0 si no hay ninguno. */
    public int obtenerUltimoConsecutivo(String tipo) throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(codigo_turno, 2) AS UNSIGNED)), 0)"
                + " FROM turnos WHERE tipo_turno = ?";
        Connection conexion = ConexionDB.getInstancia().getConexion();
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** RF10: estadísticas de todos los turnos registrados. */
    public Estadisticas generarEstadisticas() throws SQLException {
        return generarEstadisticas(null);
    }

    /**
     * RF10: turnos atendidos por ventanilla, tiempo de espera por tipo y cantidad de
     * cancelados. La espera se mide desde que se genera el turno hasta que se atiende,
     * por lo que los cancelados no entran en el promedio.
     *
     * Los cálculos (COUNT, AVG, MAX) los hace la base de datos con GROUP BY: a Java
     * solo llegan los totales, no los turnos uno por uno.
     *
     * @param fecha día a consultar, o null para todos los turnos registrados
     */
    public Estadisticas generarEstadisticas(LocalDate fecha) throws SQLException {
        Estadisticas e = new Estadisticas();
        Connection conexion = ConexionDB.getInstancia().getConexion();
        String filtroDia = fecha == null ? "" : FILTRO_DIA;

        String porVentanilla = "SELECT id_ventanilla, COUNT(*) FROM turnos"
                + " WHERE estado = 'ATENDIDO'" + filtroDia + " GROUP BY id_ventanilla";
        try (PreparedStatement ps = conexion.prepareStatement(porVentanilla)) {
            fijarDia(ps, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    if (id >= 1 && id <= e.atendidosPorVentanilla.length) {
                        e.atendidosPorVentanilla[id - 1] = rs.getInt(2);
                    }
                }
            }
        }

        // Una fila por cada combinación de tipo y estado. AVG y MAX solo tienen valor en
        // las filas ATENDIDO: en las demás hora_atencion es NULL y las funciones lo ignoran.
        String porTipoYEstado = "SELECT tipo_turno, estado, COUNT(*), AVG(" + ESPERA + "),"
                + " MAX(" + ESPERA + ") FROM turnos WHERE 1 = 1" + filtroDia
                + " GROUP BY tipo_turno, estado";
        try (PreparedStatement ps = conexion.prepareStatement(porTipoYEstado)) {
            fijarDia(ps, fecha);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int indice = Turno.prioridadDe(rs.getString(1)) - 1;
                    String estado = rs.getString(2);
                    int cantidad = rs.getInt(3);
                    if (Turno.ATENDIDO.equals(estado)) {
                        e.atendidosPorTipo[indice] = cantidad;
                        e.esperaPromedioSegundos[indice] = Math.round(rs.getDouble(4));
                        e.esperaMaximaSegundos[indice] = rs.getLong(5);
                        e.totalAtendidos += cantidad;
                    } else if (Turno.CANCELADO.equals(estado)) {
                        e.canceladosPorTipo[indice] = cantidad;
                        e.totalCancelados += cantidad;
                    } else {
                        e.totalActivos += cantidad;
                    }
                }
            }
        }
        return e;
    }

    /** Da valor a los dos parámetros de FILTRO_DIA: el inicio del día y el del siguiente. */
    private void fijarDia(PreparedStatement ps, LocalDate fecha) throws SQLException {
        if (fecha != null) {
            ps.setObject(1, fecha.atStartOfDay());
            ps.setObject(2, fecha.plusDays(1).atStartOfDay());
        }
    }

    /** Convierte cada fila del resultado en un Turno y los guarda en una Cola. */
    private Cola<Turno> leerTurnos(ResultSet rs) throws SQLException {
        Cola<Turno> turnos = new Cola<>();
        while (rs.next()) {
            LocalDateTime horaGeneracion = rs.getObject("hora_generacion", LocalDateTime.class);
            Usuario usuario = new Usuario(rs.getString("nombre_usuario"), rs.getString("documento"),
                    rs.getString("tipo_turno"), horaGeneracion);
            turnos.encolar(new Turno(rs.getString("codigo_turno"), usuario, rs.getString("estado"),
                    horaGeneracion, rs.getObject("hora_atencion", LocalDateTime.class),
                    rs.getInt("id_ventanilla")));
        }
        return turnos;
    }
}
