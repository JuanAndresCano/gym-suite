package co.analisys.clases.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReprogramarClaseRequest {
    private LocalDateTime nuevoHorario;
}
