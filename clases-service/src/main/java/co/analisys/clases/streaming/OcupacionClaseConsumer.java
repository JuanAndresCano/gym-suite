package co.analisys.clases.streaming;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consume las actualizaciones de ocupación para "actualizar el dashboard" en
 * tiempo real. Implementa ConsumerSeekAware para el mecanismo de recuperación:
 * al asignarse una partición (arranque o rebalanceo tras una caída), retoma
 * desde el último checkpoint guardado en base de datos en lugar de reprocesar
 * el topic completo o perder mensajes pendientes.
 */
@Service
public class OcupacionClaseConsumer implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(OcupacionClaseConsumer.class);

    @Autowired
    private KafkaCheckpointRepository checkpointRepository;

    @KafkaListener(topics = OcupacionClaseProducer.TOPIC, groupId = "monitoreo-grupo")
    @Transactional
    public void consumirActualizacionOcupacion(ConsumerRecord<String, OcupacionClase> record, Acknowledgment acknowledgment) {
        OcupacionClase ocupacion = record.value();
        actualizarDashboard(ocupacion);

        String key = KafkaCheckpoint.key(record.topic(), record.partition());
        KafkaCheckpoint checkpoint = checkpointRepository.findById(key)
                .orElse(new KafkaCheckpoint(key, record.offset()));
        checkpoint.setLastOffset(record.offset());
        checkpointRepository.save(checkpoint);

        acknowledgment.acknowledge();
    }

    private void actualizarDashboard(OcupacionClase ocupacion) {
        log.info("[Dashboard] Clase '{}' (id={}): {}/{} cupos ocupados a las {}",
                ocupacion.getClaseNombre(), ocupacion.getClaseId(),
                ocupacion.getOcupacionActual(), ocupacion.getCapacidadMaxima(), ocupacion.getTimestamp());
    }

    @Override
    public void onPartitionsAssigned(Map<org.apache.kafka.common.TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        assignments.keySet().forEach(topicPartition -> {
            String key = KafkaCheckpoint.key(topicPartition.topic(), topicPartition.partition());
            checkpointRepository.findById(key).ifPresent(checkpoint -> {
                long reanudarDesde = checkpoint.getLastOffset() + 1;
                log.info("Recuperación: reanudando '{}' desde el offset {} (último checkpoint conocido)",
                        key, reanudarDesde);
                callback.seek(topicPartition.topic(), topicPartition.partition(), reanudarDesde);
            });
        });
    }
}
