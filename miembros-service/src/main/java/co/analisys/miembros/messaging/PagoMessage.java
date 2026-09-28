package co.analisys.miembros.messaging;

/** Mensaje de pago encolado en "pagos-queue" para su procesamiento asincrónico. */
public class PagoMessage {
    private String pagoId;
    private Long miembroId;
    private double monto;
    private String concepto;

    public PagoMessage() {
    }

    public PagoMessage(String pagoId, Long miembroId, double monto, String concepto) {
        this.pagoId = pagoId;
        this.miembroId = miembroId;
        this.monto = monto;
        this.concepto = concepto;
    }

    public String getPagoId() { return pagoId; }
    public void setPagoId(String pagoId) { this.pagoId = pagoId; }
    public Long getMiembroId() { return miembroId; }
    public void setMiembroId(Long miembroId) { this.miembroId = miembroId; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
}
