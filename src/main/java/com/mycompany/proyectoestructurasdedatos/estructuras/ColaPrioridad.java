package com.mycompany.proyectoestructurasdedatos.estructuras;

import com.mycompany.proyectoestructurasdedatos.modelo.Turno;

/**
 * Cola de turnos ordenada por prioridad: Preferencial (1) > Entrega Rápida (2) > General (3).
 * Dentro de una misma prioridad se conserva el orden de llegada (FIFO).
 *
 * La lista se mantiene siempre ordenada, por lo que el siguiente turno a atender
 * está siempre en el frente y desencolar() y verFrente() heredados siguen siendo O(1).
 * El costo del orden se paga una sola vez, al insertar.
 *
 * Regla especial: un turno en estado LLAMADO ya está siendo atendido en la ventanilla,
 * así que ningún turno nuevo puede adelantarlo, aunque tenga más prioridad. Sin esta
 * regla, un Preferencial que llegue mientras se atiende a un General se colocaría en
 * el frente, y al pulsar "Atender" se extraería el turno equivocado.
 */
public class ColaPrioridad extends Cola<Turno> {

    /**
     * Se redefine para que nadie pueda romper el orden de la cola usando el
     * encolar() heredado, que insertaría siempre al final.
     */
    @Override
    public void encolar(Turno turno) {
        encolarConPrioridad(turno);
    }

    /**
     * Inserta el turno en la posición que le corresponde según su prioridad.
     *
     * Complejidad: O(n) en el peor caso, porque hay que recorrer la lista para
     * encontrar la posición; O(1) cuando el turno va al final.
     *
     * Ejemplo: al insertar R009 en [P003, R008, G015] se avanza hasta R008, que
     * es el último nodo que R009 no puede adelantar, y se reenlaza:
     *
     *     antes:    R008 ---------> G015
     *     después:  R008 -> R009 -> G015
     */
    public void encolarConPrioridad(Turno turno) {
        // Caso 1: cola vacía, o el turno no adelanta ni siquiera al último.
        // Va al final con el encolar() simple de Cola, en O(1). La comparación usa >=
        // para que, a igual prioridad, el recién llegado quede detrás (FIFO).
        Nodo<Turno> fin = getFin();
        if (fin == null || turno.getPrioridad() >= fin.getDato().getPrioridad()) {
            super.encolar(turno);
            return;
        }

        Nodo<Turno> nuevo = new Nodo<>(turno);
        Nodo<Turno> frente = getFrente();

        // Caso 2: el turno adelanta al frente, así que se convierte en el nuevo frente.
        if (puedeAdelantar(turno, frente.getDato())) {
            nuevo.setSiguiente(frente);
            setFrente(nuevo);
            incrementarTamanio();
            return;
        }

        // Caso 3: inserción en medio. Se busca el último nodo que el turno no puede
        // adelantar (anterior). Se mira siempre anterior.getSiguiente() porque en una
        // lista simple no se puede retroceder, y para enlazar hace falta el nodo previo.
        Nodo<Turno> anterior = frente;
        while (anterior.getSiguiente() != null
                && !puedeAdelantar(turno, anterior.getSiguiente().getDato())) {
            anterior = anterior.getSiguiente();
        }

        // El orden importa: primero el nuevo apunta al resto de la lista y solo después
        // el anterior apunta al nuevo. Al revés, se perdería la referencia al resto.
        nuevo.setSiguiente(anterior.getSiguiente());
        anterior.setSiguiente(nuevo);

        // Puede quedar de último si el único nodo es un LLAMADO de menor prioridad.
        if (nuevo.getSiguiente() == null) {
            setFin(nuevo);
        }
        incrementarTamanio();
    }

    /**
     * Un turno adelanta a otro solo si tiene estrictamente más prioridad (número menor)
     * y el otro no está LLAMADO. La comparación estricta es la que garantiza el FIFO
     * dentro de un mismo nivel.
     */
    private boolean puedeAdelantar(Turno nuevo, Turno existente) {
        return !Turno.LLAMADO.equals(existente.getEstado())
                && nuevo.getPrioridad() < existente.getPrioridad();
    }
}
