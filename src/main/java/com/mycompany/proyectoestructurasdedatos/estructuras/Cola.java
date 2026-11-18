package com.mycompany.proyectoestructurasdedatos.estructuras;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Cola FIFO (First In, First Out) implementada a mano con una lista enlazada simple.
 * No usa java.util.Queue, LinkedList, PriorityQueue ni ninguna otra colección.
 *
 * Se mantienen dos punteros, uno a cada extremo de la lista:
 *
 *     frente                       fin
 *       |                           |
 *       v                           v
 *     [A | *]--->[B | *]--->[C | null]
 *
 *   - Se extrae por el frente y se inserta por el fin.
 *   - Gracias a esos dos punteros nunca hay que recorrer la lista para encolar
 *     ni para desencolar: ambas operaciones son O(1).
 *
 * Implementa Iterable únicamente para poder recorrerla con un for-each sin
 * modificarla (por ejemplo, para pintar la cola en pantalla). Iterator es una
 * interfaz de recorrido, no una colección: no guarda datos, solo avanza por
 * nuestros propios nodos.
 *
 * @param <T> tipo de los elementos de la cola
 */
public class Cola<T> implements Iterable<T> {

    private Nodo<T> frente;
    private Nodo<T> fin;
    private int tamanio;

    public Cola() {
        this.frente = null;
        this.fin = null;
        this.tamanio = 0;
    }

    /**
     * Agrega un elemento al final de la cola. Complejidad O(1).
     *
     * El antiguo último nodo pasa a apuntar al nuevo, y el puntero fin se mueve
     * al nuevo. Si la cola estaba vacía, el nuevo nodo es también el frente.
     */
    public void encolar(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (estaVacia()) {
            frente = nuevo;
        } else {
            fin.setSiguiente(nuevo);
        }
        fin = nuevo;
        tamanio++;
    }

    /**
     * Extrae y devuelve el elemento del frente. Complejidad O(1).
     *
     * El puntero frente avanza al segundo nodo; el primero queda sin referencias
     * y el recolector de basura de Java libera su memoria.
     *
     * @return el elemento extraído, o null si la cola está vacía
     */
    public T desencolar() {
        if (estaVacia()) {
            return null;
        }
        T dato = frente.getDato();
        frente = frente.getSiguiente();
        // Si se extrajo el único nodo, fin todavía apunta a él: hay que soltarlo
        // para que la cola quede realmente vacía y el nodo se pueda liberar.
        if (frente == null) {
            fin = null;
        }
        tamanio--;
        return dato;
    }

    /**
     * Consulta el elemento del frente sin retirarlo. Complejidad O(1).
     *
     * @return el elemento del frente, o null si la cola está vacía
     */
    public T verFrente() {
        return estaVacia() ? null : frente.getDato();
    }

    /** Complejidad O(1). */
    public boolean estaVacia() {
        return frente == null;
    }

    /** Complejidad O(1): se lleva un contador en lugar de contar los nodos cada vez. */
    public int tamanio() {
        return tamanio;
    }

    /**
     * Recorre la cola del frente al fin sin modificarla: el iterador usa su propio
     * puntero (actual), de modo que frente y fin no se mueven.
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Nodo<T> actual = frente;

            @Override
            public boolean hasNext() {
                return actual != null;
            }

            @Override
            public T next() {
                if (actual == null) {
                    throw new NoSuchElementException();
                }
                T dato = actual.getDato();
                actual = actual.getSiguiente();
                return dato;
            }
        };
    }

    // ------------------------------------------------------------------
    // Acceso a la estructura interna, reservado a las subclases.
    // Los atributos siguen siendo privados (como en el UML); ColaPrioridad
    // los necesita porque inserta nodos en medio de la lista.
    // ------------------------------------------------------------------

    protected Nodo<T> getFrente() {
        return frente;
    }

    protected void setFrente(Nodo<T> frente) {
        this.frente = frente;
    }

    protected Nodo<T> getFin() {
        return fin;
    }

    protected void setFin(Nodo<T> fin) {
        this.fin = fin;
    }

    protected void incrementarTamanio() {
        tamanio++;
    }
}
