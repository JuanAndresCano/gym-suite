package co.analisys.equipos.dto;

import lombok.Data;

/** Ajuste de inventario: positivo ingresa unidades, negativo las retira. */
@Data
public class AjusteInventarioRequest {
    private int ajuste;
}
