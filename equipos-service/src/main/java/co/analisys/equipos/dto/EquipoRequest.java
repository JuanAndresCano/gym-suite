package co.analisys.equipos.dto;

import lombok.Data;

/**
 * Datos de entrada del endpoint de creacion. Se mantiene separado del agregado
 * para que Equipo no necesite setters publicos y pueda proteger sus invariantes.
 */
@Data
public class EquipoRequest {
    private String nombre;
    private String descripcion;
    private int cantidad;
}
