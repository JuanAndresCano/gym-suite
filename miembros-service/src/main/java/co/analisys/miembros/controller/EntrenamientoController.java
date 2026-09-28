package co.analisys.miembros.controller;

import co.analisys.miembros.analytics.DatosEntrenamiento;
import co.analisys.miembros.analytics.KafkaStreamsConfig;
import co.analisys.miembros.dto.EntrenamientoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/miembros/{miembroId}/entrenamientos")
@Tag(name = "Entrenamientos", description = "Registro de sesiones de entrenamiento, analizadas en tiempo real con Kafka Streams")
public class EntrenamientoController {

    @Autowired
    private KafkaTemplate<String, DatosEntrenamiento> kafkaTemplate;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Registrar una sesión de entrenamiento",
            description = "Publica el dato al topic datos-entrenamiento. Un stream processor lo agrega por miembro en ventanas de 7 días y publica el resumen en resumen-entrenamiento.")
    @ApiResponse(responseCode = "202", description = "Sesión registrada para análisis asincrónico")
    public void registrarEntrenamiento(@Parameter(description = "Id del miembro") @PathVariable Long miembroId,
                                        @RequestBody EntrenamientoRequest request) {
        DatosEntrenamiento dato = new DatosEntrenamiento(
                miembroId, request.getTipoActividad(), request.getDuracionMinutos(), request.getCalorias(), LocalDateTime.now());
        kafkaTemplate.send(KafkaStreamsConfig.DATOS_ENTRENAMIENTO_TOPIC, String.valueOf(miembroId), dato);
    }
}
