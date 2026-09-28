package co.analisys.clases.streaming;

import java.time.LocalDateTime;

/** Evento de ocupación en tiempo real, publicado al topic "ocupacion-clases". */
public class OcupacionClase {
    private Long claseId;
    private String claseNombre;
    private int ocupacionActual;
    private int capacidadMaxima;
    private LocalDateTime timestamp;

    public OcupacionClase() {
    }

    public OcupacionClase(Long claseId, String claseNombre, int ocupacionActual, int capacidadMaxima, LocalDateTime timestamp) {
        this.claseId = claseId;
        this.claseNombre = claseNombre;
        this.ocupacionActual = ocupacionActual;
        this.capacidadMaxima = capacidadMaxima;
        this.timestamp = timestamp;
    }

    public Long getClaseId() { return claseId; }
    public void setClaseId(Long claseId) { this.claseId = claseId; }
    public String getClaseNombre() { return claseNombre; }
    public void setClaseNombre(String claseNombre) { this.claseNombre = claseNombre; }
    public int getOcupacionActual() { return ocupacionActual; }
    public void setOcupacionActual(int ocupacionActual) { this.ocupacionActual = ocupacionActual; }
    public int getCapacidadMaxima() { return capacidadMaxima; }
    public void setCapacidadMaxima(int capacidadMaxima) { this.capacidadMaxima = capacidadMaxima; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
