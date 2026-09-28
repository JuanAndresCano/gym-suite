package co.analisys.miembros.messaging;

import co.analisys.miembros.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CambioHorarioListener {

    private static final Logger log = LoggerFactory.getLogger(CambioHorarioListener.class);

    @RabbitListener(queues = RabbitMQConfig.HORARIOS_MIEMBROS_QUEUE)
    public void notificarMiembros(CambioHorarioEvent evento) {
        log.info("Aviso a miembros inscritos: la clase '{}' (id={}) cambió de horario de {} a {}",
                evento.getClaseNombre(), evento.getClaseId(), evento.getHorarioAnterior(), evento.getHorarioNuevo());
    }
}
