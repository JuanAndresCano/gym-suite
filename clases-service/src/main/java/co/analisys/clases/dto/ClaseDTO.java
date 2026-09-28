package co.analisys.clases.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Representacion de una Clase enriquecida con los datos del entrenador
 * obtenidos via REST desde entrenadores-service.
 */
@Data
public class ClaseDTO {
    private Long id;
    private String nombre;
    private LocalDateTime horario;
    private int capacidadMaxima;
    private Long entrenadorId;
    private String entrenadorNombre;
    private String entrenadorEspecialidad;
    private int totalInscritos;
    private int cuposDisponibles;
    private Set<Long> miembrosInscritos;
}
