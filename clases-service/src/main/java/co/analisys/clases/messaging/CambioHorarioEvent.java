package co.analisys.clases.messaging;

import java.time.LocalDateTime;

/** Evento publicado al exchange fanout "horarios.exchange" cuando cambia el horario de una clase. */
public class CambioHorarioEvent {
    private Long claseId;
    private String claseNombre;
    private LocalDateTime horarioAnterior;
    private LocalDateTime horarioNuevo;

    public CambioHorarioEvent() {
    }

    public CambioHorarioEvent(Long claseId, String claseNombre, LocalDateTime horarioAnterior, LocalDateTime horarioNuevo) {
        this.claseId = claseId;
        this.claseNombre = claseNombre;
        this.horarioAnterior = horarioAnterior;
        this.horarioNuevo = horarioNuevo;
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
