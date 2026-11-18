package com.mycompany.proyectoestructurasdedatos.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas del ciclo de un turno en la ventanilla: PENDIENTE, LLAMADO y ATENDIDO o CANCELADO. */
class VentanillaTest {

    private Ventanilla ventanilla;
    private Turno general;
    private Turno preferencial;

    @BeforeEach
    void prepararVentanillaConDosTurnos() {
        ventanilla = new Ventanilla(2, "Funcionario de prueba");
        general = new Turno("G001", new Usuario("Ana Ruiz", "52345678", Usuario.GENERAL));
        preferencial = new Turno("P001", new Usuario("Gloria Paz", "41222333", Usuario.PREFERENCIAL));
        ventanilla.asignarTurno(general);
        ventanilla.asignarTurno(preferencial);
    }

    @Test
    void asignarGuardaLaVentanillaEnElTurno() {
        assertEquals(2, general.getIdVentanilla());
        assertEquals(2, ventanilla.getCantidadEnEspera());
        assertNull(ventanilla.getTurnoEnAtencion());
    }

    @Test
    void llamarTomaElDeMayorPrioridadSinSacarloDeLaCola() {
        Turno llamado = ventanilla.llamarSiguiente();

        assertSame(preferencial, llamado);
        assertEquals(Turno.LLAMADO, llamado.getEstado());
        assertSame(preferencial, ventanilla.getTurnoEnAtencion());
        assertEquals(2, ventanilla.getColaTurnos().tamanio());
        assertEquals(1, ventanilla.getCantidadEnEspera());
    }

    @Test
    void atenderExtraeElTurnoYRegistraLaHora() {
        ventanilla.llamarSiguiente();
        LocalDateTime hora = LocalDateTime.of(2026, 10, 18, 9, 30, 0);

        ventanilla.atenderTurno(hora);

        assertEquals(Turno.ATENDIDO, preferencial.getEstado());
        assertEquals(hora, preferencial.getHoraAtencion());
        assertNull(ventanilla.getTurnoEnAtencion());
        assertEquals(1, ventanilla.getColaTurnos().tamanio());
        assertSame(general, ventanilla.getColaTurnos().verFrente());
    }

    @Test
    void atenderSinHoraUsaLaHoraActual() {
        ventanilla.llamarSiguiente();

        ventanilla.atenderTurno();

        assertNotNull(preferencial.getHoraAtencion());
    }

    @Test
    void cancelarExtraeElTurnoSinHoraDeAtencion() {
        ventanilla.llamarSiguiente();

        ventanilla.cancelarTurno();

        assertEquals(Turno.CANCELADO, preferencial.getEstado());
        assertNull(preferencial.getHoraAtencion());
        assertEquals(1, ventanilla.getCantidadEnEspera());
    }

    @Test
    void noSePuedeAtenderNiCancelarSinHaberLlamado() {
        assertThrows(IllegalStateException.class, ventanilla::atenderTurno);
        assertThrows(IllegalStateException.class, ventanilla::cancelarTurno);
        assertEquals(2, ventanilla.getColaTurnos().tamanio());
    }

    @Test
    void llamarConLaColaVaciaDevuelveNull() {
        assertNull(new Ventanilla(1, "Funcionario de prueba").llamarSiguiente());
    }

    @Test
    void cuentaLosTurnosEnEsperaPorTipoSinElQueEstaEnAtencion() {
        assertEquals(1, ventanilla.contarEnEspera(Usuario.PREFERENCIAL));
        assertEquals(1, ventanilla.contarEnEspera(Usuario.GENERAL));
        assertEquals(0, ventanilla.contarEnEspera(Usuario.RAPIDA));

        ventanilla.llamarSiguiente();

        assertEquals(0, ventanilla.contarEnEspera(Usuario.PREFERENCIAL));
        assertEquals(1, ventanilla.contarEnEspera(Usuario.GENERAL));
    }

    @Test
    void buscaElTurnoDeUnDocumentoEnEsperaOEnAtencion() {
        ventanilla.llamarSiguiente();

        assertSame(preferencial, ventanilla.buscarTurno("41222333"));
        assertSame(general, ventanilla.buscarTurno("52345678"));
        assertNull(ventanilla.buscarTurno("99999999"));
    }
}
