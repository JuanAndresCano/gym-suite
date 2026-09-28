package co.analisys.clases.streaming;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic ocupacionClasesTopic() {
        return TopicBuilder.name(OcupacionClaseProducer.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Ack manual: el listener confirma el offset solo después de guardar el
     * checkpoint en base de datos (ver OcupacionClaseConsumer), no automáticamente
     * al recibir el mensaje. Esto es lo que hace posible retomar exactamente donde
     * se quedó tras un fallo.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OcupacionClase> kafkaListenerContainerFactory(
            ConsumerFactory<String, OcupacionClase> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, OcupacionClase> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
