package co.analisys.miembros.service;

import co.analisys.miembros.config.RabbitMQConfig;
import co.analisys.miembros.messaging.PagoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/** Cola de letra muerta de pagos: aquí terminan los pagos que no se pudieron procesar. */
@Service
public class PagoDLQService {

    private static final Logger log = LoggerFactory.getLogger(PagoDLQService.class);

    @RabbitListener(queues = RabbitMQConfig.PAGOS_DLQ)
    public void manejarPagoFallido(PagoMessage pago) {
        log.error("Pago {} requiere atención manual (miembro={}, monto={}, concepto={})",
                pago.getPagoId(), pago.getMiembroId(), pago.getMonto(), pago.getConcepto());
    }
}
