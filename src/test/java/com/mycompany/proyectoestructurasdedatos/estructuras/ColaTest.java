package com.mycompany.proyectoestructurasdedatos.estructuras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/** Pruebas de la cola FIFO sobre lista enlazada. */
class ColaTest {

    @Test
    void unaColaNuevaEstaVacia() {
        Cola<String> cola = new Cola<>();

        assertTrue(cola.estaVacia());
        assertEquals(0, cola.tamanio());
        assertNull(cola.verFrente());
        assertNull(cola.desencolar());
    }

    @Test
    void saleEnElMismoOrdenEnQueEntro() {
        Cola<String> cola = new Cola<>();
        cola.encolar("A");
        cola.encolar("B");
        cola.encolar("C");

        assertEquals(3, cola.tamanio());
        assertEquals("A", cola.desencolar());
        assertEquals("B", cola.desencolar());
        assertEquals("C", cola.desencolar());
        assertTrue(cola.estaVacia());
    }

    @Test
    void verFrenteConsultaSinExtraer() {
        Cola<String> cola = new Cola<>();
        cola.encolar("A");
        cola.encolar("B");

        assertEquals("A", cola.verFrente());
        assertEquals("A", cola.verFrente());
        assertEquals(2, cola.tamanio());
    }

    /** Al extraer el único nodo, fin también debe soltarlo; si no, el siguiente encolar falla. */
    @Test
    void sePuedeReutilizarDespuesDeVaciarla() {
        Cola<String> cola = new Cola<>();
        cola.encolar("A");
        assertEquals("A", cola.desencolar());
        assertNull(cola.desencolar());
        assertEquals(0, cola.tamanio());

        cola.encolar("B");
        cola.encolar("C");

        assertEquals(2, cola.tamanio());
        assertEquals("B", cola.desencolar());
        assertEquals("C", cola.desencolar());
        assertTrue(cola.estaVacia());
    }

    @Test
    void elIteradorRecorreDelFrenteAlFinSinModificarLaCola() {
        Cola<String> cola = new Cola<>();
        cola.encolar("A");
        cola.encolar("B");
        cola.encolar("C");

        StringBuilder recorrido = new StringBuilder();
        for (String dato : cola) {
            recorrido.append(dato);
        }

        assertEquals("ABC", recorrido.toString());
        assertEquals(3, cola.tamanio());
        assertEquals("A", cola.verFrente());
    }

    @Test
    void elIteradorAvisaCuandoNoQuedanElementos() {
        Cola<String> cola = new Cola<>();
        cola.encolar("A");
        Iterator<String> iterador = cola.iterator();

        assertTrue(iterador.hasNext());
        assertEquals("A", iterador.next());
        assertFalse(iterador.hasNext());
        assertThrows(NoSuchElementException.class, iterador::next);
    }
}
