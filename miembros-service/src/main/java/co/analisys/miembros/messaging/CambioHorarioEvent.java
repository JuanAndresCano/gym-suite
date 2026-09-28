package co.analisys.miembros.messaging;

import java.time.LocalDateTime;

/** Copia local del evento que publica clases-service al exchange fanout "horarios.exchange". */
public class CambioHorarioEvent {
    private Long claseId;
    private String claseNombre;
    private LocalDateTime horarioAnterior;
    private LocalDateTime horarioNuevo;

    public CambioHorarioEvent() {
    }

    public Long getClaseId() { return claseId; }
    public void setClaseId(Long claseId) { this.claseId = claseId; }
    public String getClaseNombre() { return claseNombre; }
    public void setClaseNombre(String claseNombre) { this.claseNombre = claseNombre; }
    public LocalDateTime getHorarioAnterior() { return horarioAnterior; }
    public void setHorarioAnterior(LocalDateTime horarioAnterior) { this.horarioAnterior = horarioAnterior; }
    public LocalDateTime getHorarioNuevo() { return horarioNuevo; }
    public void setHorarioNuevo(LocalDateTime horarioNuevo) { this.horarioNuevo = horarioNuevo; }
}
