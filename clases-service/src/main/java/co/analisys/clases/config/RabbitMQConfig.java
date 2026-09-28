package co.analisys.clases.config;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de mensajería que clases-service produce:
 *  - Cola directa de notificaciones de inscripción (consumida por miembros-service).
 *  - Exchange fanout de cambios de horario (consumido por miembros-service y
 *    entrenadores-service, cada uno con su propia cola independiente).
 */
@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICACIONES_INSCRIPCION_QUEUE = "notificaciones.inscripciones.queue";
    public static final String HORARIOS_EXCHANGE = "horarios.exchange";

    @Bean
    public Queue notificacionesInscripcionQueue() {
        return new Queue(NOTIFICACIONES_INSCRIPCION_QUEUE, true);
    }

    @Bean
    public FanoutExchange horariosExchange() {
        return new FanoutExchange(HORARIOS_EXCHANGE, true, false);
    }

    /**
     * TypePrecedence.INFERRED hace que el listener deserialice según el tipo del
     * parámetro del método, no según el header __TypeId__ del productor: cada
     * microservicio tiene su propia copia del DTO en su propio paquete, y no
     * comparten el mismo nombre de clase completamente calificado.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
