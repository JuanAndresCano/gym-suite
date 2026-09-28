package co.analisys.miembros.service;

import co.analisys.miembros.config.RabbitMQConfig;
import co.analisys.miembros.exception.PagoFallidoException;
import co.analisys.miembros.messaging.PagoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Procesa los pagos encolados en "pagos-queue". Si el pago es inválido lanza
 * PagoFallidoException: el interceptor de reintentos del listener (ver
 * spring.rabbitmq.listener.simple.retry.* en application.properties) lo vuelve a
 * intentar con backoff, y al agotar los intentos rechaza el mensaje sin
 * reencolarlo. RabbitMQ lo enruta entonces a "pagos-dlq" según el
 * dead-letter-exchange configurado en la cola (ver RabbitMQConfig).
 *
 * No se lanza AmqpRejectAndDontRequeueException aquí: rechazaría el mensaje en el
 * primer fallo y los reintentos nunca contarían.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    @RabbitListener(queues = RabbitMQConfig.PAGOS_QUEUE)
    public void procesarPago(PagoMessage pago) {
        if (!procesoPagoExitoso(pago)) {
            log.warn("Intento fallido al procesar el pago {} (monto={})", pago.getPagoId(), pago.getMonto());
            throw new PagoFallidoException("Fallo en el procesamiento del pago " + pago.getPagoId());
        }
        log.info("Pago {} procesado exitosamente: miembro={}, monto={}, concepto={}",
                pago.getPagoId(), pago.getMiembroId(), pago.getMonto(), pago.getConcepto());
    }

    /** Simula la validación de un pago: se rechaza si el monto no es positivo. */
    private boolean procesoPagoExitoso(PagoMessage pago) {
        return pago.getMonto() > 0;
    }
}
