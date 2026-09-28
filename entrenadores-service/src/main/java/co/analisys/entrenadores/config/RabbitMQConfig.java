package co.analisys.entrenadores.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Se suscribe al exchange fanout "horarios.exchange" (declarado también por
 * clases-service) con su propia cola independiente: cada suscriptor del fanout
 * recibe una copia del mismo mensaje sin acoplarse entre sí.
 */
@Configuration
public class RabbitMQConfig {

    public static final String HORARIOS_EXCHANGE = "horarios.exchange";
    public static final String HORARIOS_ENTRENADORES_QUEUE = "horarios.notificaciones.entrenadores.queue";

    @Bean
    public FanoutExchange horariosExchange() {
        return new FanoutExchange(HORARIOS_EXCHANGE, true, false);
    }

    @Bean
    public Queue horariosEntrenadoresQueue() {
        return new Queue(HORARIOS_ENTRENADORES_QUEUE, true);
    }

    @Bean
    public Binding horariosEntrenadoresBinding(Queue horariosEntrenadoresQueue, FanoutExchange horariosExchange) {
        return BindingBuilder.bind(horariosEntrenadoresQueue).to(horariosExchange);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
