package co.analisys.miembros.dto;

import lombok.Data;

@Data
public class EntrenamientoRequest {
    private String tipoActividad;
    private int duracionMinutos;
    private int calorias;
}
