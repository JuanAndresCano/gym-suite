package co.analisys.clases.messaging;

import java.time.LocalDateTime;

/** Evento publicado cuando un miembro se inscribe exitosamente en una clase. */
public class InscripcionNotificacion {
    private Long miembroId;
    private Long claseId;
    private String claseNombre;
    private LocalDateTime horario;

    public InscripcionNotificacion() {
    }

    public InscripcionNotificacion(Long miembroId, Long claseId, String claseNombre, LocalDateTime horario) {
        this.miembroId = miembroId;
        this.claseId = claseId;
        this.claseNombre = claseNombre;
        this.horario = horario;
    }

    public Long getMiembroId() { return miembroId; }
    public void setMiembroId(Long miembroId) { this.miembroId = miembroId; }
    public Long getClaseId() { return claseId; }
    public void setClaseId(Long claseId) { this.claseId = claseId; }
    public String getClaseNombre() { return claseNombre; }
    public void setClaseNombre(String claseNombre) { this.claseNombre = claseNombre; }
    public LocalDateTime getHorario() { return horario; }
    public void setHorario(LocalDateTime horario) { this.horario = horario; }
}
