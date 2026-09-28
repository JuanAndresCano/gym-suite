package co.analisys.miembros.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MiembroRequest {
    private String nombre;
    private String email;
    private LocalDate fechaInscripcion;
}
