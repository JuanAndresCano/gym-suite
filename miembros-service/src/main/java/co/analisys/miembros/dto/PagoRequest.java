package co.analisys.miembros.dto;

import lombok.Data;

@Data
public class PagoRequest {
    private Long miembroId;
    private double monto;
    private String concepto;
}
