package co.analisys.miembros.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

/** Pago procesado con exito. Su id es el pagoId que se devolvio al encolarlo. */
@Entity
public class Pago {
    @Id
    private String id;
    private Long miembroId;
    private double monto;
    private String concepto;
    private LocalDateTime fecha;

    protected Pago() {
        // Requerido por JPA
    }

    public Pago(String id, Long miembroId, double monto, String concepto, LocalDateTime fecha) {
        this.id = id;
        this.miembroId = miembroId;
        this.monto = monto;
        this.concepto = concepto;
        this.fecha = fecha;
    }

    public String getId() { return id; }
    public Long getMiembroId() { return miembroId; }
    public double getMonto() { return monto; }
    public String getConcepto() { return concepto; }
    public LocalDateTime getFecha() { return fecha; }
}
