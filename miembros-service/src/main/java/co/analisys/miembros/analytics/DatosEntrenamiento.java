package co.analisys.miembros.analytics;

import java.time.LocalDateTime;

/** Registro de una sesión de entrenamiento, publicado al topic "datos-entrenamiento". */
public class DatosEntrenamiento {
    private Long miembroId;
    private String tipoActividad;
    private int duracionMinutos;
    private int calorias;
    private LocalDateTime fecha;

    public DatosEntrenamiento() {
    }

    public DatosEntrenamiento(Long miembroId, String tipoActividad, int duracionMinutos, int calorias, LocalDateTime fecha) {
        this.miembroId = miembroId;
        this.tipoActividad = tipoActividad;
        this.duracionMinutos = duracionMinutos;
        this.calorias = calorias;
        this.fecha = fecha;
    }

    public Long getMiembroId() { return miembroId; }
    public void setMiembroId(Long miembroId) { this.miembroId = miembroId; }
    public String getTipoActividad() { return tipoActividad; }
    public void setTipoActividad(String tipoActividad) { this.tipoActividad = tipoActividad; }
    public int getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(int duracionMinutos) { this.duracionMinutos = duracionMinutos; }
    public int getCalorias() { return calorias; }
    public void setCalorias(int calorias) { this.calorias = calorias; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
