package com.mycompany.proyectoestructurasdedatos.estructuras;

/**
 * Unidad básica de la lista enlazada simple sobre la que se construyen las colas.
 *
 * Cada nodo guarda un dato y un "puntero" (referencia en Java) al nodo que le
 * sigue. El último nodo de la cadena tiene siguiente == null, y ese null es lo
 * que indica el final de la lista:
 *
 *     [P003 | *]--->[R008 | *]--->[G015 | null]
 *
 * @param <T> tipo de dato almacenado. Al ser genérico, la misma clase sirve para
 *            cualquier tipo sin usar Object ni hacer conversiones (casts).
 */
public class Nodo<T> {

    private T dato;
    private Nodo<T> siguiente;

    /**
     * Crea un nodo suelto: todavía no apunta a nadie, y es quien lo inserte
     * el que debe enlazarlo con la cadena.
     */
    public Nodo(T dato) {
        this.dato = dato;
        this.siguiente = null;
    }

    public T getDato() {
        return dato;
    }

    public void setDato(T dato) {
        this.dato = dato;
    }

    public Nodo<T> getSiguiente() {
        return siguiente;
    }

    /** Cambia a qué nodo apunta este. Toda inserción o extracción se reduce a esto. */
    public void setSiguiente(Nodo<T> siguiente) {
        this.siguiente = siguiente;
    }
}
