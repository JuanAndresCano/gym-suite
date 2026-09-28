package co.analisys.miembros.analytics;

/**
 * Agregado que Kafka Streams construye por miembro y ventana de tiempo (7 días).
 * Se recalcula en el store local con cada nuevo DatosEntrenamiento de la ventana.
 */
public class ResumenEntrenamiento {
    private Long miembroId;
    private int totalSesiones;
    private int totalMinutos;
    private int totalCalorias;

    public ResumenEntrenamiento() {
    }

    public ResumenEntrenamiento actualizar(DatosEntrenamiento dato) {
        this.miembroId = dato.getMiembroId();
        this.totalSesiones += 1;
        this.totalMinutos += dato.getDuracionMinutos();
        this.totalCalorias += dato.getCalorias();
        return this;
    }

    public Long getMiembroId() { return miembroId; }
    public int getTotalSesiones() { return totalSesiones; }
    public int getTotalMinutos() { return totalMinutos; }
    public int getTotalCalorias() { return totalCalorias; }
}
