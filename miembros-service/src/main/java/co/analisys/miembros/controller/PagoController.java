package co.analisys.miembros.controller;

import co.analisys.miembros.config.RabbitMQConfig;
import co.analisys.miembros.dto.PagoRequest;
import co.analisys.miembros.messaging.PagoMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pagos")
@Tag(name = "Pagos", description = "Procesamiento asincrónico de pagos con manejo de fallos vía Dead Letter Queue")
public class PagoController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Encolar un pago para procesamiento asincrónico",
            description = "El pago se procesa de forma asincrónica en pagos-queue. Un monto <= 0 provoca un fallo de procesamiento y el mensaje termina en pagos-dlq.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pago encolado, se procesará de forma asincrónica")
    })
    public Map<String, String> encolarPago(@RequestBody PagoRequest request) {
        String pagoId = UUID.randomUUID().toString();
        PagoMessage mensaje = new PagoMessage(pagoId, request.getMiembroId(), request.getMonto(), request.getConcepto());
        rabbitTemplate.convertAndSend(RabbitMQConfig.PAGOS_QUEUE, mensaje);
        return Map.of("pagoId", pagoId, "estado", "en_proceso");
    }
}
