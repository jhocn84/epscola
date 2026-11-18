package com.mycompany.proyectoestructurasdedatos.estructuras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.proyectoestructurasdedatos.modelo.Turno;
import com.mycompany.proyectoestructurasdedatos.modelo.Usuario;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Pruebas de la cola de prioridad: Preferencial > Entrega Rápida > General, con FIFO por nivel. */
class ColaPrioridadTest {

    private static Turno turno(String codigo, String tipo) {
        return new Turno(codigo, new Usuario("Usuario " + codigo, "10000", tipo));
    }

    /** Encola un turno deduciendo el tipo de la letra del código: P, R o G. */
    private static Turno encolar(ColaPrioridad cola, String codigo) {
        String tipo = codigo.startsWith("P") ? Usuario.PREFERENCIAL
                : codigo.startsWith("R") ? Usuario.RAPIDA : Usuario.GENERAL;
        Turno turno = turno(codigo, tipo);
        cola.encolarConPrioridad(turno);
        return turno;
    }

    /** Códigos del frente al fin, separados por espacios. */
    private static String orden(ColaPrioridad cola) {
        StringBuilder codigos = new StringBuilder();
        for (Turno turno : cola) {
            if (codigos.length() > 0) {
                codigos.append(' ');
            }
            codigos.append(turno.getCodigo());
        }
        return codigos.toString();
    }

    @Test
    void ordenaPorPrioridadAunqueLleguenAlReves() {
        ColaPrioridad cola = new ColaPrioridad();
        encolar(cola, "G001");
        encolar(cola, "R001");
        encolar(cola, "P001");

        assertEquals("P001 R001 G001", orden(cola));
        assertEquals(3, cola.tamanio());
    }

    @Test
    void conservaElOrdenDeLlegadaDentroDeCadaNivel() {
        ColaPrioridad cola = new ColaPrioridad();
        encolar(cola, "G001");
        encolar(cola, "P001");
        encolar(cola, "R001");
        encolar(cola, "G002");
        encolar(cola, "P002");
        encolar(cola, "R002");
        encolar(cola, "P003");

        assertEquals("P001 P002 P003 R001 R002 G001 G002", orden(cola));
    }

    /** El ejemplo del documento: R009 entra entre R008 y G015. */
    @Test
    void insertaEnMedioDeLaLista() {
        ColaPrioridad cola = new ColaPrioridad();
        encolar(cola, "P003");
        encolar(cola, "R008");
        encolar(cola, "G015");

        encolar(cola, "R009");

        assertEquals("P003 R008 R009 G015", orden(cola));
    }

    @Test
    void desencolarEntregaSiempreElDeMayorPrioridad() {
        ColaPrioridad cola = new ColaPrioridad();
        encolar(cola, "G001");
        encolar(cola, "R001");
        encolar(cola, "P001");
        encolar(cola, "G002");

        assertEquals("P001", cola.desencolar().getCodigo());
        assertEquals("R001", cola.desencolar().getCodigo());
        assertEquals("G001", cola.desencolar().getCodigo());
        assertEquals("G002", cola.desencolar().getCodigo());
        assertTrue(cola.estaVacia());
    }

    /** La regla especial: quien ya está siendo atendido no pierde su lugar en el frente. */
    @Test
    void nadieAdelantaAlTurnoLlamado() {
        ColaPrioridad cola = new ColaPrioridad();
        Turno general = encolar(cola, "G001");
        encolar(cola, "G002");
        general.setEstado(Turno.LLAMADO);

        encolar(cola, "P001");
        encolar(cola, "R001");
        encolar(cola, "P002");

        assertEquals("G001 P001 P002 R001 G002", orden(cola));
        assertEquals("G001", cola.verFrente().getCodigo());
    }

    /** Si el nuevo queda de último tras un LLAMADO, fin debe apuntar a él. */
    @Test
    void actualizaElFinAlInsertarDespuesDeUnUnicoTurnoLlamado() {
        ColaPrioridad cola = new ColaPrioridad();
        encolar(cola, "G001").setEstado(Turno.LLAMADO);

        encolar(cola, "P001");
        encolar(cola, "G002");

        assertEquals("G001 P001 G002", orden(cola));
        assertEquals(3, cola.tamanio());
    }

    @Test
    void elEncolarHeredadoTambienRespetaLaPrioridad() {
        ColaPrioridad cola = new ColaPrioridad();
        cola.encolar(turno("G001", Usuario.GENERAL));
        cola.encolar(turno("P001", Usuario.PREFERENCIAL));

        assertEquals("P001 G001", orden(cola));
    }

    /**
     * Encola 500 turnos de tipos al azar y verifica, al desencolarlos todos, que la
     * prioridad nunca retrocede y que dentro de un mismo nivel salen por orden de llegada.
     * La semilla es fija para que la prueba sea siempre la misma.
     */
    @Test
    void mantieneElOrdenConMuchosTurnosAlAzar() {
        String[] tipos = {Usuario.PREFERENCIAL, Usuario.RAPIDA, Usuario.GENERAL};
        Random azar = new Random(2026);
        ColaPrioridad cola = new ColaPrioridad();
        int total = 500;
        for (int llegada = 1; llegada <= total; llegada++) {
            // El código guarda el orden de llegada, para poder comprobar el FIFO.
            cola.encolarConPrioridad(turno(String.valueOf(llegada), tipos[azar.nextInt(tipos.length)]));
        }
        assertEquals(total, cola.tamanio());

        int prioridadAnterior = 0;
        int llegadaAnterior = 0;
        int extraidos = 0;
        while (!cola.estaVacia()) {
            Turno turno = cola.desencolar();
            int llegada = Integer.parseInt(turno.getCodigo());
            assertTrue(turno.getPrioridad() >= prioridadAnterior, "la prioridad retrocedió");
            if (turno.getPrioridad() == prioridadAnterior) {
                assertTrue(llegada > llegadaAnterior, "se rompió el FIFO dentro de un nivel");
            }
            prioridadAnterior = turno.getPrioridad();
            llegadaAnterior = llegada;
            extraidos++;
        }
        assertEquals(total, extraidos);
        assertEquals(0, cola.tamanio());
    }
}
