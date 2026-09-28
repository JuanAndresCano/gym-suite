package co.analisys.entrenadores.messaging;

import co.analisys.entrenadores.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CambioHorarioListener {

    private static final Logger log = LoggerFactory.getLogger(CambioHorarioListener.class);

    @RabbitListener(queues = RabbitMQConfig.HORARIOS_ENTRENADORES_QUEUE)
    public void notificarEntrenador(CambioHorarioEvent evento) {
        log.info("Aviso al entrenador: la clase '{}' (id={}) cambió de horario de {} a {}",
                evento.getClaseNombre(), evento.getClaseId(), evento.getHorarioAnterior(), evento.getHorarioNuevo());
    }
}
