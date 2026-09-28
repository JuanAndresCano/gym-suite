package co.analisys.clases.dto;

import lombok.Data;

/**
 * Vista local del agregado Entrenador, que pertenece a otro contexto acotado.
 * Solo contiene los campos que el contexto de Clases necesita conocer.
 */
@Data
public class EntrenadorDTO {
    private Long id;
    private String nombre;
    private String especialidad;
}
