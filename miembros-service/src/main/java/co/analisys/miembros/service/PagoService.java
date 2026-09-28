package co.analisys.miembros.service;

import co.analisys.miembros.config.RabbitMQConfig;
import co.analisys.miembros.exception.PagoFallidoException;
import co.analisys.miembros.messaging.PagoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Procesa los pagos encolados en "pagos-queue". Si el pago es inválido, rechaza
 * el mensaje sin reencolarlo: RabbitMQ lo enruta automáticamente a "pagos-dlq"
 * según el dead-letter-exchange configurado en la cola (ver RabbitMQConfig).
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    @RabbitListener(queues = RabbitMQConfig.PAGOS_QUEUE)
    public void procesarPago(PagoMessage pago) {
        try {
            if (!procesoPagoExitoso(pago)) {
                throw new PagoFallidoException("Fallo en el procesamiento del pago " + pago.getPagoId());
            }
            log.info("Pago {} procesado exitosamente: miembro={}, monto={}, concepto={}",
                    pago.getPagoId(), pago.getMiembroId(), pago.getMonto(), pago.getConcepto());
        } catch (Exception e) {
            log.warn("Error procesando el pago {}: {}. Se envía a la DLQ.", pago.getPagoId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Error en el pago, enviando a DLQ", e);
        }
    }

    /** Simula la validación de un pago: se rechaza si el monto no es positivo. */
    private boolean procesoPagoExitoso(PagoMessage pago) {
        return pago.getMonto() > 0;
    }
}
