package ar.turnos.odontologia.bloqueo;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Rango [inicio, fin) en el que el consultorio no atiende. Pueden solaparse entre si (KB 04). */
@Entity
@Table(name = "bloqueo", indexes = @Index(name = "bloqueo_rango_idx", columnList = "inicio, fin"))
public class Bloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Column(length = 200)
    private String motivo;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Bloqueo() {
    }

    public Bloqueo(Instant inicio, Instant fin, String motivo) {
        this.inicio = inicio;
        this.fin = fin;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public Instant getInicio() {
        return inicio;
    }

    public void setInicio(Instant inicio) {
        this.inicio = inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public void setFin(Instant fin) {
        this.fin = fin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
