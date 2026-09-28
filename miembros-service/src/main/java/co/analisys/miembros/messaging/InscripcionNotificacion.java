package co.analisys.miembros.messaging;

import java.time.LocalDateTime;

/** Copia local del evento que publica clases-service al inscribir un miembro. */
public class InscripcionNotificacion {
    private Long miembroId;
    private Long claseId;
    private String claseNombre;
    private LocalDateTime horario;

    public InscripcionNotificacion() {
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
