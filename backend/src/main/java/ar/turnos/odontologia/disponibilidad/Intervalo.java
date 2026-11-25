package ar.turnos.odontologia.disponibilidad;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Rango semiabierto {@code [inicio, fin)}: dos rangos que solo se tocan en un borde NO se solapan
 * (RN-TUR-02). Es inmutable y no tiene dependencias de Spring ni de persistencia.
 */
public record Intervalo(Instant inicio, Instant fin) {

    public Intervalo {
        if (inicio == null || fin == null || !inicio.isBefore(fin)) {
            throw new IllegalArgumentException("El intervalo requiere inicio < fin");
        }
    }

    /** Verdadero si comparten algo de tiempo; tocar solo un borde no cuenta. */
    public boolean interseca(Intervalo otro) {
        return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
    }

    /** Verdadero si {@code otro} cabe completo adentro de este intervalo. */
    public boolean contiene(Intervalo otro) {
        return !otro.inicio.isBefore(inicio) && !otro.fin.isAfter(fin);
    }

    /**
     * Resta todos los ocupantes y devuelve las piezas libres resultantes, ordenadas. Los ocupantes
     * que no intersectan no modifican el resultado.
     */
    public List<Intervalo> restar(Collection<Intervalo> ocupados) {
        List<Intervalo> piezas = new ArrayList<>();
        piezas.add(this);
        for (Intervalo ocupado : ocupados) {
            List<Intervalo> siguientes = new ArrayList<>();
            for (Intervalo pieza : piezas) {
                siguientes.addAll(pieza.restarUno(ocupado));
            }
            piezas = siguientes;
        }
        piezas.sort(Comparator.comparing(Intervalo::inicio));
        return List.copyOf(piezas);
    }

    private List<Intervalo> restarUno(Intervalo ocupado) {
        if (!interseca(ocupado)) {
            return List.of(this);
        }
        List<Intervalo> resultado = new ArrayList<>(2);
        if (inicio.isBefore(ocupado.inicio())) {
            resultado.add(new Intervalo(inicio, ocupado.inicio()));
        }
        if (ocupado.fin().isBefore(fin)) {
            resultado.add(new Intervalo(ocupado.fin(), fin));
        }
        return resultado;
    }
}
