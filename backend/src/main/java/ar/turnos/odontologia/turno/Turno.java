package ar.turnos.odontologia.turno;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Reserva de un paciente para una prestacion. {@code fin} se persiste (inicio + duracion al reservar).
 * La no superposicion de turnos activos la garantiza la restriccion {@code turno_sin_solapamiento}
 * de schema-extra.sql, no esta clase.
 */
@Entity
@Table(name = "turno",
        uniqueConstraints = @UniqueConstraint(name = "turno_token_uk", columnNames = "token"),
        indexes = {
                @Index(name = "turno_inicio_idx", columnList = "inicio"),
                @Index(name = "turno_estado_inicio_idx", columnList = "estado, inicio") })
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false)
    private Prestacion prestacion;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTurno estado;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "email_contacto", nullable = false, length = 150)
    private String emailContacto;

    @Column(name = "telefono_contacto", nullable = false, length = 30)
    private String telefonoContacto;

    @Column(name = "consentimiento_at")
    private Instant consentimientoAt;

    @Column(name = "recordatorio_enviado", nullable = false)
    private boolean recordatorioEnviado = false;

    @Column(name = "alerta_pendiente_enviada", nullable = false)
    private boolean alertaPendienteEnviada = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por", length = 20)
    private CanceladoPor canceladoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Turno() {
    }

    public Turno(Paciente paciente, Prestacion prestacion, Instant inicio, Instant fin, EstadoTurno estado,
            String token, String emailContacto, String telefonoContacto) {
        this.paciente = paciente;
        this.prestacion = prestacion;
        this.inicio = inicio;
        this.fin = fin;
        this.estado = estado;
        this.token = token;
        this.emailContacto = emailContacto;
        this.telefonoContacto = telefonoContacto;
    }

    public Long getId() {
        return id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Prestacion getPrestacion() {
        return prestacion;
    }

    public void setPrestacion(Prestacion prestacion) {
        this.prestacion = prestacion;
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

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmailContacto() {
        return emailContacto;
    }

    public void setEmailContacto(String emailContacto) {
        this.emailContacto = emailContacto;
    }

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }

    public Instant getConsentimientoAt() {
        return consentimientoAt;
    }

    public void setConsentimientoAt(Instant consentimientoAt) {
        this.consentimientoAt = consentimientoAt;
    }

    public boolean isRecordatorioEnviado() {
        return recordatorioEnviado;
    }

    public void setRecordatorioEnviado(boolean recordatorioEnviado) {
        this.recordatorioEnviado = recordatorioEnviado;
    }

    public boolean isAlertaPendienteEnviada() {
        return alertaPendienteEnviada;
    }

    public void setAlertaPendienteEnviada(boolean alertaPendienteEnviada) {
        this.alertaPendienteEnviada = alertaPendienteEnviada;
    }

    public CanceladoPor getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(CanceladoPor canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
