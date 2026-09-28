package co.analisys.miembros.messaging;

import co.analisys.miembros.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InscripcionNotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(InscripcionNotificacionListener.class);

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACIONES_INSCRIPCION_QUEUE)
    public void notificarInscripcion(InscripcionNotificacion notificacion) {
        log.info("Notificación: el miembro {} fue inscrito en la clase '{}' (id={}) programada para {}",
                notificacion.getMiembroId(), notificacion.getClaseNombre(), notificacion.getClaseId(), notificacion.getHorario());
    }
}
