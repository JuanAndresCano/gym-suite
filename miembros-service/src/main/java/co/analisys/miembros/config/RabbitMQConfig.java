package co.analisys.miembros.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Notificaciones de inscripción (cola directa, publicada por clases-service)
    public static final String NOTIFICACIONES_INSCRIPCION_QUEUE = "notificaciones.inscripciones.queue";

    // Cambios de horario (exchange fanout, publicado por clases-service)
    public static final String HORARIOS_EXCHANGE = "horarios.exchange";
    public static final String HORARIOS_MIEMBROS_QUEUE = "horarios.notificaciones.miembros.queue";

    // Pagos con Dead Letter Queue
    public static final String PAGOS_QUEUE = "pagos-queue";
    public static final String PAGOS_DLQ = "pagos-dlq";

    @Bean
    public Queue notificacionesInscripcionQueue() {
        return new Queue(NOTIFICACIONES_INSCRIPCION_QUEUE, true);
    }

    @Bean
    public FanoutExchange horariosExchange() {
        return new FanoutExchange(HORARIOS_EXCHANGE, true, false);
    }

    @Bean
    public Queue horariosMiembrosQueue() {
        return new Queue(HORARIOS_MIEMBROS_QUEUE, true);
    }

    @Bean
    public Binding horariosMiembrosBinding(Queue horariosMiembrosQueue, FanoutExchange horariosExchange) {
        return BindingBuilder.bind(horariosMiembrosQueue).to(horariosExchange);
    }

    /**
     * Cola principal de pagos: si el consumidor rechaza el mensaje (ver PagoService)
     * o se agota el TTL, RabbitMQ lo reenvía automáticamente a "pagos-dlq" usando
     * el exchange por defecto ("") con el nombre de la cola como routing key.
     */
    @Bean
    public Queue pagosQueue() {
        return QueueBuilder.durable(PAGOS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", PAGOS_DLQ)
                .withArgument("x-message-ttl", 30000)
                .build();
    }

    @Bean
    public Queue pagosDLQ() {
        return QueueBuilder.durable(PAGOS_DLQ).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
