package co.analisys.clases.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClaseRequest {
    private String nombre;
    private LocalDateTime horario;
    private int capacidadMaxima;
    private Long entrenadorId;
}
