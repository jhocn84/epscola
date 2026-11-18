package com.mycompany.proyectoestructurasdedatos.vista;

import com.mycompany.proyectoestructurasdedatos.estructuras.Cola;
import com.mycompany.proyectoestructurasdedatos.modelo.SistemaEPS;
import com.mycompany.proyectoestructurasdedatos.modelo.Turno;
import com.mycompany.proyectoestructurasdedatos.modelo.Usuario;
import com.mycompany.proyectoestructurasdedatos.modelo.Ventanilla;
import com.mycompany.proyectoestructurasdedatos.persistencia.ConexionDB;
import com.mycompany.proyectoestructurasdedatos.persistencia.TurnoDAO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Interfaz gráfica principal, para uso del personal de la EPS. Tiene tres pestañas:
 *
 *   - Ventanillas:  digiturno (registro de usuarios) y estado en tiempo real de las
 *                   4 ventanillas, con sus botones Llamar / Atender / Cancelar.
 *   - Historial:    consulta de turnos con filtros por fecha, tipo, ventanilla y estado.
 *   - Estadísticas: atendidos por ventanilla, espera promedio por tipo y cancelados.
 *
 * Esta clase solo muestra datos y captura acciones: toda la lógica se delega en
 * SistemaEPS. Todo se ejecuta en el hilo de eventos de Swing (EDT), incluido el
 * Timer del reloj, así que las colas nunca se usan desde dos hilos a la vez.
 */
public class VentanaPrincipal extends JFrame {

    // Paleta de la interfaz. Cada tipo de turno conserva su color en toda la aplicación.
    private static final Color FONDO = new Color(0xF2F5F7);
    private static final Color SUPERFICIE = Color.WHITE;
    private static final Color SUPERFICIE_SUAVE = new Color(0xF7F9FA);
    private static final Color BORDE = new Color(0xD5DDE3);
    private static final Color TEXTO = new Color(0x1C2B36);
    private static final Color TEXTO_SUAVE = new Color(0x566673);
    private static final Color PRIMARIO = new Color(0x0B5F66);
    private static final Color COLOR_PREFERENCIAL = new Color(0x7A2E8E);
    private static final Color COLOR_RAPIDA = new Color(0xA85A00);
    private static final Color COLOR_GENERAL = new Color(0x1F6FB2);
    private static final Color COLOR_ATENDER = new Color(0x1B7A4B);
    private static final Color COLOR_CANCELAR = new Color(0xB3342E);
    private static final Color COLOR_INACTIVO = new Color(0xE3E8EC);

    private static final Font FUENTE_BASE = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FUENTE_NEGRITA = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FUENTE_PEQUENA = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FUENTE_TITULO = new Font("Segoe UI", Font.BOLD, 16);

    private static final DateTimeFormatter FORMATO_RELOJ = DateTimeFormatter
            .ofPattern("EEE d MMM yyyy, HH:mm:ss", Locale.forLanguageTag("es-CO"));
    private static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] TIPOS = {Usuario.GENERAL, Usuario.PREFERENCIAL, Usuario.RAPIDA};
    private static final String[] ETIQUETAS_TIPO = {"General (G)", "Preferencial (P)", "Entrega Rápida (R)"};
    private static final String[] ESTADOS =
            {Turno.PENDIENTE, Turno.LLAMADO, Turno.ATENDIDO, Turno.CANCELADO};
    /** Periodos de las estadísticas. El índice 1 limita la consulta a los turnos de hoy. */
    private static final String[] PERIODOS = {"Todo el historial", "Solo hoy"};

    /** Cuántos turnos en espera se dibujan en cada tarjeta; el resto se resume como "+N". */
    private static final int MAX_TURNOS_VISIBLES = 7;

    private final SistemaEPS sistema;

    // Encabezado
    private final JLabel lblReloj = new JLabel();
    private final JLabel lblTotalEspera = new JLabel();
    private final JLabel lblLlamado = new JLabel("Sin llamados todavía");

    // Digiturno
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtDocumento = new JTextField();
    private final JComboBox<String> cmbTipo = new JComboBox<>(ETIQUETAS_TIPO);
    private final JLabel lblErrorRegistro = new JLabel(" ");
    private final JLabel lblTicketCodigo = new JLabel("—", SwingConstants.CENTER);
    private final JLabel lblTicketUsuario = new JLabel("Aún no se ha generado un turno", SwingConstants.CENTER);
    private final JLabel lblTicketVentanilla = new JLabel(" ", SwingConstants.CENTER);

    private final TarjetaVentanilla[] tarjetas = new TarjetaVentanilla[SistemaEPS.NUM_VENTANILLAS];

    // Historial
    private final JTextField txtFiltroFecha = new JTextField(9);
    private final JComboBox<String> cmbFiltroTipo = new JComboBox<>(conOpcionTodos("Todos", ETIQUETAS_TIPO));
    private final JComboBox<String> cmbFiltroVentanilla = new JComboBox<>(opcionesVentanilla());
    private final JComboBox<String> cmbFiltroEstado = new JComboBox<>(conOpcionTodos("Todos", ESTADOS));
    private final JLabel lblResultadoHistorial = new JLabel(" ");
    private final DefaultTableModel modeloHistorial = crearModeloTabla(
            "Código", "Usuario", "Documento", "Tipo", "Ventanilla",
            "Generación", "Atención", "Espera", "Estado");

    // Estadísticas
    private final JLabel lblTotalAtendidos = new JLabel("0");
    private final JLabel lblTotalCancelados = new JLabel("0");
    private final JLabel lblTotalActivos = new JLabel("0");
    private final JLabel lblEstadisticasHora = new JLabel(" ");
    private final JComboBox<String> cmbPeriodo = new JComboBox<>(PERIODOS);
    private final DefaultTableModel modeloPorVentanilla =
            crearModeloTabla("Ventanilla", "Funcionario", "Turnos atendidos");
    private final DefaultTableModel modeloPorTipo = crearModeloTabla(
            "Tipo de turno", "Atendidos", "Espera promedio", "Espera máxima", "Cancelados");

    public VentanaPrincipal(SistemaEPS sistema) {
        super("Farmacia EPS - Gestión de Turnos");
        this.sistema = sistema;

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setFont(FUENTE_NEGRITA);
        pestanas.addTab("Ventanillas", crearPestanaVentanillas());
        pestanas.addTab("Historial", crearPestanaHistorial());
        pestanas.addTab("Estadísticas", crearPestanaEstadisticas());
        // Historial y estadísticas se consultan a la base de datos al entrar a la pestaña.
        pestanas.addChangeListener(e -> {
            if (pestanas.getSelectedIndex() == 1) {
                consultarHistorial();
            } else if (pestanas.getSelectedIndex() == 2) {
                mostrarEstadisticas();
            }
        });

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(FONDO);
        raiz.add(crearEncabezado(), BorderLayout.NORTH);
        raiz.add(pestanas, BorderLayout.CENTER);
        setContentPane(raiz);

        // Refresca reloj y tiempos de espera cada segundo. javax.swing.Timer dispara
        // sus eventos en el EDT, por lo que aquí no hay problemas de concurrencia.
        Timer temporizador = new Timer(1000, e -> {
            actualizarReloj();
            mostrarEstadoVentanillas();
        });
        temporizador.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                temporizador.stop();
                ConexionDB.getInstancia().cerrarConexion();
            }
        });

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 720));
        setSize(1280, 760);
        setLocationRelativeTo(null);
        actualizarReloj();
        mostrarEstadoVentanillas();
    }

    // ==================================================================
    // Acciones
    // ==================================================================

    /** RF07: vuelca en pantalla el estado actual de las 4 ventanillas. */
    private void mostrarEstadoVentanillas() {
        for (Ventanilla ventanilla : sistema.getEstadoVentanillas()) {
            tarjetas[ventanilla.getId() - 1].actualizar(ventanilla);
        }
        lblTotalEspera.setText(String.valueOf(sistema.getTotalEnEspera()));
    }

    /** RF01 y RF02: valida el formulario, genera el turno y muestra el ticket. */
    private void registrarUsuario() {
        // Los espacios repetidos se compactan para no guardar "Ana    Ruiz".
        String nombre = txtNombre.getText().trim().replaceAll("\\s+", " ");
        String documento = txtDocumento.getText().trim();

        String error = validarRegistro(nombre, documento);
        if (error != null) {
            lblErrorRegistro.setText(error);
            return;
        }
        lblErrorRegistro.setText(" ");

        String tipo = TIPOS[cmbTipo.getSelectedIndex()];
        try {
            Turno turno = sistema.registrarUsuario(new Usuario(nombre, documento, tipo));
            lblTicketCodigo.setText(turno.getCodigo());
            lblTicketCodigo.setForeground(colorDeTipo(tipo));
            lblTicketUsuario.setText(nombre);
            lblTicketVentanilla.setText("Asignado a la Ventanilla " + turno.getIdVentanilla());
            txtNombre.setText("");
            txtDocumento.setText("");
            cmbTipo.setSelectedIndex(0);
            txtNombre.requestFocusInWindow();
            mostrarEstadoVentanillas();
        } catch (SQLException e) {
            mostrarErrorBD(e);
        } catch (IllegalStateException e) {
            // El documento ya tiene un turno en espera o en atención.
            mostrarAviso(e.getMessage());
        }
    }

    /** @return el mensaje de error, o null si los datos son válidos */
    private String validarRegistro(String nombre, String documento) {
        if (nombre.isEmpty()) {
            return "Ingrese el nombre del usuario.";
        }
        if (nombre.length() > 100 || !nombre.matches("[\\p{L} .'-]+")) {
            return "Nombre: solo letras, máximo 100.";
        }
        if (!documento.matches("\\d{5,20}")) {
            return "Documento: solo dígitos, de 5 a 20.";
        }
        return null;
    }

    /** RF04: llama al siguiente turno y anuncia código, usuario y ventanilla. */
    private void llamarSiguiente(int idVentanilla) {
        try {
            Turno turno = sistema.llamarSiguiente(idVentanilla);
            if (turno != null) {
                lblLlamado.setText(turno.getCodigo() + "  ·  " + turno.getUsuario().getNombre()
                        + "  →  Ventanilla " + idVentanilla);
            }
            mostrarEstadoVentanillas();
        } catch (SQLException e) {
            mostrarErrorBD(e);
        } catch (IllegalStateException e) {
            mostrarAviso(e.getMessage());
        }
    }

    /** RF05: finaliza la entrega; el turno sale de la cola y se guarda la hora de atención. */
    private void atenderTurno(int idVentanilla) {
        try {
            sistema.atenderTurno(idVentanilla);
            mostrarEstadoVentanillas();
        } catch (SQLException e) {
            mostrarErrorBD(e);
        } catch (IllegalStateException e) {
            mostrarAviso(e.getMessage());
        }
    }

    /** RF06: cancela el turno llamado, previa confirmación porque no se puede deshacer. */
    private void cancelarTurno(int idVentanilla) {
        Turno turno = sistema.getVentanilla(idVentanilla).getTurnoEnAtencion();
        if (turno == null) {
            return;
        }
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿Cancelar el turno " + turno.getCodigo() + " de " + turno.getUsuario().getNombre() + "?",
                "Cancelar turno", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            sistema.cancelarTurno(idVentanilla);
            mostrarEstadoVentanillas();
        } catch (SQLException e) {
            mostrarErrorBD(e);
        } catch (IllegalStateException e) {
            mostrarAviso(e.getMessage());
        }
    }

    /** RF08: consulta los turnos aplicando los filtros seleccionados. */
    private void consultarHistorial() {
        LocalDate fecha = null;
        String textoFecha = txtFiltroFecha.getText().trim();
        if (!textoFecha.isEmpty()) {
            try {
                fecha = LocalDate.parse(textoFecha);
            } catch (DateTimeParseException e) {
                lblResultadoHistorial.setForeground(COLOR_CANCELAR);
                lblResultadoHistorial.setText("Fecha inválida: use el formato AAAA-MM-DD");
                return;
            }
        }
        // En cada combo, el índice 0 es "Todos": ese filtro no se aplica.
        int indiceTipo = cmbFiltroTipo.getSelectedIndex();
        int indiceVentanilla = cmbFiltroVentanilla.getSelectedIndex();
        int indiceEstado = cmbFiltroEstado.getSelectedIndex();
        String tipo = indiceTipo == 0 ? null : TIPOS[indiceTipo - 1];
        Integer ventanilla = indiceVentanilla == 0 ? null : indiceVentanilla;
        String estado = indiceEstado == 0 ? null : ESTADOS[indiceEstado - 1];

        try {
            // Primero se consulta y después se vacía la tabla: si la base de datos
            // falla, en pantalla se conserva el resultado anterior.
            Cola<Turno> turnos = sistema.consultarHistorial(fecha, tipo, ventanilla, estado);
            modeloHistorial.setRowCount(0);
            for (Turno turno : turnos) {
                modeloHistorial.addRow(new Object[] {
                    turno.getCodigo(),
                    turno.getUsuario().getNombre(),
                    turno.getUsuario().getDocumento(),
                    etiquetaDeTipo(turno.getUsuario().getTipoTurno()),
                    turno.getIdVentanilla(),
                    turno.getHoraGeneracion().format(FORMATO_FECHA_HORA),
                    turno.getHoraAtencion() == null ? "—" : turno.getHoraAtencion().format(FORMATO_FECHA_HORA),
                    turno.getHoraAtencion() == null ? "—" : formatearDuracion(
                            Duration.between(turno.getHoraGeneracion(), turno.getHoraAtencion()).getSeconds()),
                    turno.getEstado()
                });
            }
            int filas = modeloHistorial.getRowCount();
            lblResultadoHistorial.setForeground(TEXTO_SUAVE);
            if (filas == TurnoDAO.LIMITE_HISTORIAL) {
                lblResultadoHistorial.setText("Se muestran los " + filas
                        + " más recientes: use los filtros para acotar");
            } else {
                lblResultadoHistorial.setText(filas == 1 ? "1 turno encontrado" : filas + " turnos encontrados");
            }
        } catch (SQLException e) {
            mostrarErrorBD(e);
        }
    }

    private void limpiarFiltros() {
        txtFiltroFecha.setText("");
        cmbFiltroTipo.setSelectedIndex(0);
        cmbFiltroVentanilla.setSelectedIndex(0);
        cmbFiltroEstado.setSelectedIndex(0);
        consultarHistorial();
    }

    /** RF10: consulta las estadísticas y las vuelca en los indicadores y las tablas. */
    private void mostrarEstadisticas() {
        LocalDate fecha = cmbPeriodo.getSelectedIndex() == 1 ? LocalDate.now() : null;
        try {
            TurnoDAO.Estadisticas estadisticas = sistema.generarEstadisticas(fecha);
            lblTotalAtendidos.setText(String.valueOf(estadisticas.totalAtendidos));
            lblTotalCancelados.setText(String.valueOf(estadisticas.totalCancelados));
            lblTotalActivos.setText(String.valueOf(estadisticas.totalActivos));

            modeloPorVentanilla.setRowCount(0);
            for (Ventanilla ventanilla : sistema.getEstadoVentanillas()) {
                modeloPorVentanilla.addRow(new Object[] {
                    "Ventanilla " + ventanilla.getId(),
                    ventanilla.getNombreFuncionario(),
                    estadisticas.atendidosPorVentanilla[ventanilla.getId() - 1]
                });
            }

            modeloPorTipo.setRowCount(0);
            for (String tipo : new String[] {Usuario.PREFERENCIAL, Usuario.RAPIDA, Usuario.GENERAL}) {
                int indice = Turno.prioridadDe(tipo) - 1;
                boolean sinAtendidos = estadisticas.atendidosPorTipo[indice] == 0;
                modeloPorTipo.addRow(new Object[] {
                    etiquetaDeTipo(tipo),
                    estadisticas.atendidosPorTipo[indice],
                    sinAtendidos ? "—" : formatearDuracion(estadisticas.esperaPromedioSegundos[indice]),
                    sinAtendidos ? "—" : formatearDuracion(estadisticas.esperaMaximaSegundos[indice]),
                    estadisticas.canceladosPorTipo[indice]
                });
            }
            lblEstadisticasHora.setText("Actualizado: "
                    + LocalDateTime.now().format(FORMATO_FECHA_HORA));
        } catch (SQLException e) {
            mostrarErrorBD(e);
        }
    }

    private void actualizarReloj() {
        lblReloj.setText(LocalDateTime.now().format(FORMATO_RELOJ));
    }

    private void mostrarErrorBD(SQLException e) {
        JOptionPane.showMessageDialog(this,
                "No se pudo completar la operación en la base de datos.\n"
                + "No se realizó ningún cambio. Verifique que MySQL esté iniciado.\n\nDetalle: " + e.getMessage(),
                "Error de base de datos", JOptionPane.ERROR_MESSAGE);
    }

    private void mostrarAviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // ==================================================================
    // Construcción de la interfaz
    // ==================================================================

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout(24, 0));
        encabezado.setBackground(PRIMARIO);
        encabezado.setBorder(new EmptyBorder(10, 18, 10, 18));

        JPanel titulo = new JPanel(new GridLayout(2, 1));
        titulo.setOpaque(false);
        titulo.add(crearEtiqueta("Farmacia EPS", FUENTE_TITULO, Color.WHITE));
        titulo.add(crearEtiqueta("Gestión de turnos para entrega de medicamentos", FUENTE_PEQUENA, Color.WHITE));
        encabezado.add(titulo, BorderLayout.WEST);

        JPanel llamado = new JPanel(new GridLayout(2, 1));
        llamado.setOpaque(false);
        llamado.add(crearEtiqueta("ÚLTIMO LLAMADO", FUENTE_PEQUENA, Color.WHITE));
        lblLlamado.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLlamado.setForeground(Color.WHITE);
        llamado.add(lblLlamado);
        encabezado.add(llamado, BorderLayout.CENTER);

        JPanel derecha = new JPanel(new GridLayout(2, 1));
        derecha.setOpaque(false);
        lblReloj.setFont(FUENTE_PEQUENA);
        lblReloj.setForeground(Color.WHITE);
        lblReloj.setHorizontalAlignment(SwingConstants.RIGHT);
        derecha.add(lblReloj);

        JPanel total = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        total.setOpaque(false);
        total.add(crearEtiqueta("Usuarios en espera:", FUENTE_BASE, Color.WHITE));
        lblTotalEspera.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTotalEspera.setForeground(Color.WHITE);
        total.add(lblTotalEspera);
        derecha.add(total);
        encabezado.add(derecha, BorderLayout.EAST);
        return encabezado;
    }

    private JPanel crearPestanaVentanillas() {
        JPanel grilla = new JPanel(new GridLayout(2, 2, 12, 12));
        grilla.setBackground(FONDO);
        grilla.setBorder(new EmptyBorder(12, 12, 12, 12));
        for (Ventanilla ventanilla : sistema.getEstadoVentanillas()) {
            TarjetaVentanilla tarjeta = new TarjetaVentanilla(ventanilla);
            tarjetas[ventanilla.getId() - 1] = tarjeta;
            grilla.add(tarjeta);
        }

        JPanel pestana = new JPanel(new BorderLayout());
        pestana.setBackground(FONDO);
        pestana.add(crearPanelDigiturno(), BorderLayout.WEST);
        pestana.add(grilla, BorderLayout.CENTER);
        return pestana;
    }

    private JPanel crearPanelDigiturno() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SUPERFICIE);
        panel.setPreferredSize(new Dimension(280, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDE), new EmptyBorder(14, 16, 14, 16)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        agregarFila(panel, c, crearEtiqueta("Digiturno", FUENTE_TITULO, TEXTO), 10);
        agregarFila(panel, c, crearEtiqueta("Nombre completo", FUENTE_BASE, TEXTO), 3);
        txtNombre.setFont(FUENTE_BASE);
        agregarFila(panel, c, txtNombre, 8);
        agregarFila(panel, c, crearEtiqueta("Número de documento", FUENTE_BASE, TEXTO), 3);
        txtDocumento.setFont(FUENTE_BASE);
        agregarFila(panel, c, txtDocumento, 8);
        agregarFila(panel, c, crearEtiqueta("Tipo de atención", FUENTE_BASE, TEXTO), 3);
        cmbTipo.setFont(FUENTE_BASE);
        agregarFila(panel, c, cmbTipo, 6);

        lblErrorRegistro.setFont(FUENTE_PEQUENA);
        lblErrorRegistro.setForeground(COLOR_CANCELAR);
        agregarFila(panel, c, lblErrorRegistro, 6);

        JButton btnGenerar = crearBoton("Generar turno", PRIMARIO);
        btnGenerar.addActionListener(e -> registrarUsuario());
        agregarFila(panel, c, btnGenerar, 14);
        // Enter en cualquiera de los dos campos equivale a pulsar el botón.
        txtNombre.addActionListener(e -> registrarUsuario());
        txtDocumento.addActionListener(e -> registrarUsuario());

        agregarFila(panel, c, crearTicket(), 14);

        agregarFila(panel, c, crearEtiqueta("Orden de atención", FUENTE_NEGRITA, TEXTO), 6);
        agregarFila(panel, c, crearFilaLeyenda("P", COLOR_PREFERENCIAL,
                "<html>Adulto mayor, gestante, discapacidad<br>o bebé en brazos</html>"), 6);
        agregarFila(panel, c, crearFilaLeyenda("R", COLOR_RAPIDA, "Entrega rápida: con cita asignada"), 6);
        agregarFila(panel, c, crearFilaLeyenda("G", COLOR_GENERAL, "General: fórmula ordinaria"), 0);

        // Relleno invisible que empuja el contenido hacia arriba.
        c.weighty = 1;
        JPanel relleno = new JPanel();
        relleno.setOpaque(false);
        panel.add(relleno, c);
        return panel;
    }

    private JPanel crearTicket() {
        JPanel ticket = new JPanel(new GridLayout(3, 1));
        ticket.setBackground(SUPERFICIE_SUAVE);
        ticket.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE), new EmptyBorder(8, 8, 10, 8)));
        lblTicketCodigo.setFont(new Font("Segoe UI", Font.BOLD, 30));
        lblTicketCodigo.setForeground(TEXTO_SUAVE);
        lblTicketUsuario.setFont(FUENTE_BASE);
        lblTicketUsuario.setForeground(TEXTO);
        lblTicketVentanilla.setFont(FUENTE_NEGRITA);
        lblTicketVentanilla.setForeground(TEXTO);
        ticket.add(lblTicketCodigo);
        ticket.add(lblTicketUsuario);
        ticket.add(lblTicketVentanilla);
        return ticket;
    }

    private JPanel crearFilaLeyenda(String letra, Color color, String descripcion) {
        JPanel fila = new JPanel(new BorderLayout(8, 0));
        fila.setOpaque(false);
        JPanel contenedorChip = new JPanel(new BorderLayout());
        contenedorChip.setOpaque(false);
        contenedorChip.add(crearChip(letra, color), BorderLayout.NORTH);
        fila.add(contenedorChip, BorderLayout.WEST);
        fila.add(crearEtiqueta(descripcion, FUENTE_PEQUENA, TEXTO_SUAVE), BorderLayout.CENTER);
        return fila;
    }

    private JPanel crearPestanaHistorial() {
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filtros.setBackground(SUPERFICIE);
        filtros.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDE));

        filtros.add(crearEtiqueta("Fecha (AAAA-MM-DD):", FUENTE_BASE, TEXTO));
        txtFiltroFecha.setFont(FUENTE_BASE);
        txtFiltroFecha.addActionListener(e -> consultarHistorial());
        filtros.add(txtFiltroFecha);
        JButton btnHoy = crearBoton("Hoy", TEXTO_SUAVE);
        btnHoy.addActionListener(e -> {
            txtFiltroFecha.setText(LocalDate.now().toString());
            consultarHistorial();
        });
        filtros.add(btnHoy);
        cmbFiltroTipo.setFont(FUENTE_BASE);
        cmbFiltroVentanilla.setFont(FUENTE_BASE);
        cmbFiltroEstado.setFont(FUENTE_BASE);
        filtros.add(crearEtiqueta("Tipo:", FUENTE_BASE, TEXTO));
        filtros.add(cmbFiltroTipo);
        filtros.add(crearEtiqueta("Ventanilla:", FUENTE_BASE, TEXTO));
        filtros.add(cmbFiltroVentanilla);
        filtros.add(crearEtiqueta("Estado:", FUENTE_BASE, TEXTO));
        filtros.add(cmbFiltroEstado);

        JButton btnBuscar = crearBoton("Buscar", PRIMARIO);
        btnBuscar.addActionListener(e -> consultarHistorial());
        filtros.add(btnBuscar);
        JButton btnLimpiar = crearBoton("Limpiar", TEXTO_SUAVE);
        btnLimpiar.addActionListener(e -> limpiarFiltros());
        filtros.add(btnLimpiar);
        lblResultadoHistorial.setFont(FUENTE_BASE);
        filtros.add(lblResultadoHistorial);

        JTable tabla = crearTabla(modeloHistorial);
        tabla.setAutoCreateRowSorter(true);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(60);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(190);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(60);
        tabla.getColumnModel().getColumn(5).setPreferredWidth(135);
        tabla.getColumnModel().getColumn(6).setPreferredWidth(135);
        tabla.getColumnModel().getColumn(7).setPreferredWidth(60);
        // Código y Estado usan color además del texto, para ubicar rápido cada caso:
        // el código lleva el color de su tipo de turno, igual que en las ventanillas.
        tabla.getColumnModel().getColumn(0).setCellRenderer(new CeldaTabla() {
            @Override
            protected Color colorDe(Object valor) {
                return colorDeCodigo(String.valueOf(valor));
            }
        });
        tabla.getColumnModel().getColumn(8).setCellRenderer(new CeldaTabla() {
            @Override
            protected Color colorDe(Object valor) {
                return colorDeEstado(String.valueOf(valor));
            }
        });

        JPanel pestana = new JPanel(new BorderLayout());
        pestana.setBackground(FONDO);
        pestana.add(filtros, BorderLayout.NORTH);
        pestana.add(crearScroll(tabla), BorderLayout.CENTER);
        return pestana;
    }

    private JPanel crearPestanaEstadisticas() {
        JPanel indicadores = new JPanel(new GridLayout(1, 3, 12, 0));
        indicadores.setOpaque(false);
        indicadores.add(crearIndicador("Turnos atendidos", lblTotalAtendidos));
        indicadores.add(crearIndicador("Turnos cancelados", lblTotalCancelados));
        indicadores.add(crearIndicador("Turnos activos (en espera o en atención)", lblTotalActivos));

        JPanel tablas = new JPanel(new GridLayout(1, 2, 12, 0));
        tablas.setOpaque(false);
        tablas.add(crearSeccionTabla("Atendidos por ventanilla", crearTabla(modeloPorVentanilla)));
        tablas.add(crearSeccionTabla("Espera y cancelados por tipo", crearTabla(modeloPorTipo)));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barra.setOpaque(false);
        barra.add(crearEtiqueta("Periodo:", FUENTE_BASE, TEXTO));
        cmbPeriodo.setFont(FUENTE_BASE);
        cmbPeriodo.addActionListener(e -> mostrarEstadisticas());
        barra.add(cmbPeriodo);
        JButton btnActualizar = crearBoton("Actualizar", PRIMARIO);
        btnActualizar.addActionListener(e -> mostrarEstadisticas());
        barra.add(btnActualizar);
        lblEstadisticasHora.setFont(FUENTE_PEQUENA);
        lblEstadisticasHora.setForeground(TEXTO_SUAVE);
        barra.add(lblEstadisticasHora);

        JPanel superior = new JPanel(new BorderLayout(0, 12));
        superior.setOpaque(false);
        superior.add(barra, BorderLayout.NORTH);
        superior.add(indicadores, BorderLayout.CENTER);

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        contenido.setOpaque(false);
        contenido.add(superior, BorderLayout.NORTH);
        contenido.add(tablas, BorderLayout.CENTER);
        contenido.add(crearEtiqueta("La espera se mide desde que se genera el turno hasta que se marca"
                + " como atendido. Los turnos cancelados no entran en el promedio.",
                FUENTE_PEQUENA, TEXTO_SUAVE), BorderLayout.SOUTH);

        JPanel pestana = new JPanel(new BorderLayout());
        pestana.setBackground(FONDO);
        pestana.setBorder(new EmptyBorder(12, 12, 12, 12));
        pestana.add(contenido, BorderLayout.NORTH);
        return pestana;
    }

    private JPanel crearIndicador(String titulo, JLabel valor) {
        JPanel indicador = new JPanel(new BorderLayout(0, 2));
        indicador.setBackground(SUPERFICIE);
        indicador.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE), new EmptyBorder(12, 16, 12, 16)));
        valor.setFont(new Font("Segoe UI", Font.BOLD, 34));
        valor.setForeground(TEXTO);
        indicador.add(valor, BorderLayout.CENTER);
        indicador.add(crearEtiqueta(titulo, FUENTE_BASE, TEXTO_SUAVE), BorderLayout.SOUTH);
        return indicador;
    }

    private JPanel crearSeccionTabla(String titulo, JTable tabla) {
        JPanel seccion = new JPanel(new BorderLayout(0, 6));
        seccion.setOpaque(false);
        seccion.add(crearEtiqueta(titulo, FUENTE_NEGRITA, TEXTO), BorderLayout.NORTH);
        // Alto justo para las 4 filas de la tabla más larga, así ambas quedan alineadas.
        tabla.setPreferredScrollableViewportSize(new Dimension(300, tabla.getRowHeight() * 4));
        seccion.add(crearScroll(tabla), BorderLayout.CENTER);
        return seccion;
    }

    private static void agregarFila(JPanel panel, GridBagConstraints c, Component componente, int margenInferior) {
        c.insets = new Insets(0, 0, margenInferior, 0);
        panel.add(componente, c);
        c.gridy++;
    }

    private static JLabel crearEtiqueta(String texto, Font fuente, Color color) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(fuente);
        etiqueta.setForeground(color);
        return etiqueta;
    }

    /** Etiqueta con fondo de color para los códigos de turno y las letras de tipo. */
    private static JLabel crearChip(String texto, Color color) {
        JLabel chip = new JLabel(texto);
        chip.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chip.setForeground(Color.WHITE);
        chip.setOpaque(true);
        chip.setBackground(color);
        chip.setBorder(new EmptyBorder(2, 7, 2, 7));
        return chip;
    }

    private static JButton crearBoton(String texto, Color color) {
        JButton boton = new JButton(texto);
        // BasicButtonUI: el aspecto de Windows ignoraría el color de fondo del botón.
        boton.setUI(new BasicButtonUI());
        boton.setFont(FUENTE_NEGRITA);
        boton.setBackground(color);
        boton.setForeground(Color.WHITE);
        boton.setBorder(new EmptyBorder(7, 8, 7, 8));
        boton.setFocusPainted(false);
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return boton;
    }

    private static void activarBoton(JButton boton, boolean activo, Color color) {
        boton.setEnabled(activo);
        boton.setBackground(activo ? color : COLOR_INACTIVO);
        boton.setCursor(Cursor.getPredefinedCursor(activo ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private static DefaultTableModel crearModeloTabla(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
    }

    private static JTable crearTabla(DefaultTableModel modelo) {
        JTable tabla = new JTable(modelo);
        tabla.setFont(FUENTE_BASE);
        tabla.setRowHeight(28);
        tabla.setGridColor(BORDE);
        // Solo líneas horizontales: con las filas de fondo alterno, las verticales sobran.
        tabla.setShowVerticalLines(false);
        tabla.setIntercellSpacing(new Dimension(0, 1));
        tabla.setFillsViewportHeight(true);
        tabla.setDefaultRenderer(Object.class, new CeldaTabla());
        tabla.getTableHeader().setFont(FUENTE_NEGRITA);
        tabla.getTableHeader().setReorderingAllowed(false);
        return tabla;
    }

    /**
     * Celda de tabla con margen interno y filas de fondo alterno. Las columnas que
     * llevan un color propio (código, estado) redefinen colorDe().
     */
    private static class CeldaTabla extends DefaultTableCellRenderer {

        /** Mismo margen que usa el encabezado, para que cada dato quede bajo su título. */
        private static final EmptyBorder MARGEN = new EmptyBorder(0, 5, 0, 5);

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            // Se pasa foco = false: en una tabla de solo lectura, el recuadro de la
            // celda enfocada no aporta nada.
            super.getTableCellRendererComponent(tabla, valor, seleccionada, false, fila, columna);
            Color color = colorDe(valor);
            setBorder(MARGEN);
            setFont(color == null ? FUENTE_BASE : FUENTE_NEGRITA);
            if (!seleccionada) {
                setBackground(fila % 2 == 0 ? SUPERFICIE : SUPERFICIE_SUAVE);
                setForeground(color == null ? TEXTO : color);
            }
            return this;
        }

        /** @return el color del texto para este valor, o null para usar el normal */
        protected Color colorDe(Object valor) {
            return null;
        }
    }

    private static JScrollPane crearScroll(JTable tabla) {
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.getViewport().setBackground(SUPERFICIE);
        scroll.setBorder(BorderFactory.createLineBorder(BORDE));
        return scroll;
    }

    private static String[] conOpcionTodos(String primera, String[] opciones) {
        String[] resultado = new String[opciones.length + 1];
        resultado[0] = primera;
        System.arraycopy(opciones, 0, resultado, 1, opciones.length);
        return resultado;
    }

    /** Opciones del filtro de ventanilla: "Todas" y un número por cada ventanilla. */
    private static String[] opcionesVentanilla() {
        String[] opciones = new String[SistemaEPS.NUM_VENTANILLAS + 1];
        opciones[0] = "Todas";
        for (int id = 1; id <= SistemaEPS.NUM_VENTANILLAS; id++) {
            opciones[id] = String.valueOf(id);
        }
        return opciones;
    }

    private static String etiquetaDeTipo(String tipo) {
        switch (tipo) {
            case Usuario.PREFERENCIAL:
                return "Preferencial";
            case Usuario.RAPIDA:
                return "Entrega Rápida";
            default:
                return "General";
        }
    }

    private static Color colorDeTipo(String tipo) {
        switch (tipo) {
            case Usuario.PREFERENCIAL:
                return COLOR_PREFERENCIAL;
            case Usuario.RAPIDA:
                return COLOR_RAPIDA;
            default:
                return COLOR_GENERAL;
        }
    }

    /** Color del tipo de turno al que pertenece un código, según su letra inicial. */
    private static Color colorDeCodigo(String codigo) {
        if (codigo.startsWith("P")) {
            return COLOR_PREFERENCIAL;
        }
        return codigo.startsWith("R") ? COLOR_RAPIDA : COLOR_GENERAL;
    }

    private static Color colorDeEstado(String estado) {
        switch (estado) {
            case Turno.ATENDIDO:
                return COLOR_ATENDER;
            case Turno.CANCELADO:
                return COLOR_CANCELAR;
            case Turno.LLAMADO:
                return PRIMARIO;
            default:
                return TEXTO_SUAVE;
        }
    }

    /** Convierte segundos a mm:ss, o a h:mm:ss si pasa de una hora. */
    private static String formatearDuracion(long segundos) {
        long positivos = Math.max(0, segundos);
        long horas = positivos / 3600;
        long minutos = (positivos % 3600) / 60;
        long resto = positivos % 60;
        return horas > 0
                ? String.format("%d:%02d:%02d", horas, minutos, resto)
                : String.format("%02d:%02d", minutos, resto);
    }

    // ==================================================================
    // Tarjeta de una ventanilla
    // ==================================================================

    /** Panel con el estado y los botones de una ventanilla (RF04, RF05, RF06, RF07). */
    private final class TarjetaVentanilla extends JPanel {

        private final JLabel lblEstado = new JLabel();
        private final JLabel lblCodigo = new JLabel();
        private final JLabel lblUsuario = new JLabel();
        private final JLabel lblDetalle = new JLabel();
        private final JLabel lblEnEspera = new JLabel();
        private final JPanel panelCola = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        private final JButton btnLlamar = crearBoton("Llamar siguiente", PRIMARIO);
        private final JButton btnAtender = crearBoton("Atender", COLOR_ATENDER);
        private final JButton btnCancelar = crearBoton("Cancelar", COLOR_CANCELAR);

        /** Códigos en espera que hay dibujados ahora mismo, del frente al fin. */
        private String colaDibujada;

        TarjetaVentanilla(Ventanilla ventanilla) {
            super(new BorderLayout(0, 8));
            setBackground(SUPERFICIE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDE), new EmptyBorder(10, 14, 12, 14)));

            int id = ventanilla.getId();
            btnLlamar.addActionListener(e -> llamarSiguiente(id));
            btnAtender.addActionListener(e -> atenderTurno(id));
            btnCancelar.addActionListener(e -> cancelarTurno(id));

            JPanel titulo = new JPanel(new BorderLayout(10, 0));
            titulo.setOpaque(false);
            JPanel nombres = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            nombres.setOpaque(false);
            nombres.add(crearEtiqueta("Ventanilla " + id, FUENTE_TITULO, TEXTO));
            nombres.add(crearEtiqueta("   " + ventanilla.getNombreFuncionario(), FUENTE_BASE, TEXTO_SUAVE));
            titulo.add(nombres, BorderLayout.WEST);
            lblEstado.setFont(FUENTE_NEGRITA);
            titulo.add(lblEstado, BorderLayout.EAST);
            add(titulo, BorderLayout.NORTH);

            JPanel atencion = new JPanel(new BorderLayout(14, 0));
            atencion.setBackground(SUPERFICIE_SUAVE);
            atencion.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDE), new EmptyBorder(4, 12, 6, 12)));
            lblCodigo.setFont(new Font("Segoe UI", Font.BOLD, 26));
            atencion.add(lblCodigo, BorderLayout.WEST);
            JPanel datos = new JPanel(new GridLayout(2, 1));
            datos.setOpaque(false);
            lblUsuario.setFont(FUENTE_NEGRITA);
            lblUsuario.setForeground(TEXTO);
            lblDetalle.setFont(FUENTE_PEQUENA);
            lblDetalle.setForeground(TEXTO_SUAVE);
            datos.add(lblUsuario);
            datos.add(lblDetalle);
            atencion.add(datos, BorderLayout.CENTER);

            lblEnEspera.setFont(FUENTE_BASE);
            lblEnEspera.setForeground(TEXTO);
            panelCola.setOpaque(false);

            JPanel centro = new JPanel(new GridBagLayout());
            centro.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.NORTH;
            agregarFila(centro, c, atencion, 8);
            agregarFila(centro, c, lblEnEspera, 5);
            agregarFila(centro, c, panelCola, 0);
            c.weighty = 1;
            JPanel relleno = new JPanel();
            relleno.setOpaque(false);
            centro.add(relleno, c);
            add(centro, BorderLayout.CENTER);

            JPanel botones = new JPanel(new GridLayout(1, 3, 6, 0));
            botones.setOpaque(false);
            botones.add(btnLlamar);
            botones.add(btnAtender);
            botones.add(btnCancelar);
            add(botones, BorderLayout.SOUTH);
        }

        void actualizar(Ventanilla ventanilla) {
            Turno enAtencion = ventanilla.getTurnoEnAtencion();
            int enEspera = ventanilla.getCantidadEnEspera();

            if (enAtencion == null) {
                lblEstado.setText("Libre");
                lblEstado.setForeground(TEXTO_SUAVE);
                lblCodigo.setText("—");
                lblCodigo.setForeground(TEXTO_SUAVE);
                lblUsuario.setText("Sin turno en atención");
                lblDetalle.setText(enEspera > 0 ? "Puede llamar al siguiente turno" : "No hay usuarios en espera");
            } else {
                String tipo = enAtencion.getUsuario().getTipoTurno();
                long espera = Duration.between(enAtencion.getHoraGeneracion(), LocalDateTime.now()).getSeconds();
                lblEstado.setText("En atención");
                lblEstado.setForeground(COLOR_ATENDER);
                lblCodigo.setText(enAtencion.getCodigo());
                lblCodigo.setForeground(colorDeTipo(tipo));
                lblUsuario.setText(enAtencion.getUsuario().getNombre());
                lblDetalle.setText(etiquetaDeTipo(tipo) + "  ·  Doc. " + enAtencion.getUsuario().getDocumento()
                        + "  ·  Espera: " + formatearDuracion(espera));
            }

            lblEnEspera.setText("En espera: " + enEspera
                    + "     (P: " + ventanilla.contarEnEspera(Usuario.PREFERENCIAL)
                    + "  ·  R: " + ventanilla.contarEnEspera(Usuario.RAPIDA)
                    + "  ·  G: " + ventanilla.contarEnEspera(Usuario.GENERAL) + ")");

            // El reloj llama a este método cada segundo, pero la cola casi nunca cambia
            // entre una llamada y otra: solo se vuelve a dibujar si es distinta a la que
            // ya está en pantalla. Así no se recrean las etiquetas sin necesidad.
            StringBuilder codigos = new StringBuilder();
            for (Turno turno : ventanilla.getColaTurnos()) {
                if (turno != enAtencion) {
                    codigos.append(turno.getCodigo()).append(' ');
                }
            }
            if (!codigos.toString().equals(colaDibujada)) {
                colaDibujada = codigos.toString();
                dibujarCola(ventanilla, enAtencion, enEspera);
            }

            activarBoton(btnLlamar, enAtencion == null && enEspera > 0, PRIMARIO);
            activarBoton(btnAtender, enAtencion != null, COLOR_ATENDER);
            activarBoton(btnCancelar, enAtencion != null, COLOR_CANCELAR);
        }

        /**
         * Dibuja los turnos en espera tal como están en la cola en memoria, del frente
         * al fin: lo que se ve es el orden real de atención. Al pasar el cursor sobre
         * un código se muestra a quién pertenece.
         */
        private void dibujarCola(Ventanilla ventanilla, Turno enAtencion, int enEspera) {
            panelCola.removeAll();
            int dibujados = 0;
            for (Turno turno : ventanilla.getColaTurnos()) {
                if (turno == enAtencion) {
                    continue;
                }
                if (dibujados == MAX_TURNOS_VISIBLES) {
                    panelCola.add(crearEtiqueta("+" + (enEspera - dibujados), FUENTE_NEGRITA, TEXTO_SUAVE));
                    break;
                }
                Usuario usuario = turno.getUsuario();
                JLabel chip = crearChip(turno.getCodigo(), colorDeTipo(usuario.getTipoTurno()));
                chip.setToolTipText(usuario.getNombre() + "  ·  Doc. " + usuario.getDocumento()
                        + "  ·  Llegó a las " + turno.getHoraGeneracion().format(FORMATO_HORA));
                panelCola.add(chip);
                dibujados++;
            }
            if (dibujados == 0) {
                panelCola.add(crearEtiqueta(" ", FUENTE_NEGRITA, TEXTO_SUAVE));
            }
            panelCola.revalidate();
            panelCola.repaint();
        }
    }

    // ==================================================================
    // Punto de entrada
    // ==================================================================

    /**
     * Antes de abrir la ventana, recupera de la base de datos los turnos que quedaron
     * activos. MySQL es obligatorio porque el sistema persiste todos los turnos.
     *
     * @return false si no hay conexión y el usuario decide salir
     */
    private static boolean cargarDatosConReintento(SistemaEPS sistema) {
        while (true) {
            try {
                sistema.cargarEstadoGuardado();
                return true;
            } catch (SQLException e) {
                String[] opciones = {"Reintentar", "Salir"};
                int opcion = JOptionPane.showOptionDialog(null,
                        "No se pudo conectar con la base de datos.\n\n"
                        + "1. Verifique que MySQL esté iniciado (panel de control de XAMPP).\n"
                        + "2. Verifique que exista la base eps_farmacia (database/eps_farmacia.sql).\n\n"
                        + "Detalle: " + e.getMessage(),
                        "Error de conexión", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                        null, opciones, opciones[0]);
                if (opcion != 0) {
                    return false;
                }
            } catch (RuntimeException e) {
                // Hubo conexión, pero un turno guardado no se pudo ubicar (por ejemplo,
                // una ventanilla inexistente tras editar la tabla a mano). Reintentar
                // no lo arregla: se informa y se sale.
                JOptionPane.showMessageDialog(null,
                        "Los turnos guardados en la base de datos no son válidos.\n"
                        + "Revise la tabla turnos, o vacíela con TRUNCATE TABLE turnos.\n\n"
                        + "Detalle: " + e.getMessage(),
                        "Datos no válidos", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("No se pudo aplicar el aspecto del sistema: " + e.getMessage());
            }
            SistemaEPS sistema = new SistemaEPS();
            if (cargarDatosConReintento(sistema)) {
                new VentanaPrincipal(sistema).setVisible(true);
            }
        });
    }
}
