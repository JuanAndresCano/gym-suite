package co.analisys.clases.streaming;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OcupacionClaseProducer {

    public static final String TOPIC = "ocupacion-clases";

    @Autowired
    private KafkaTemplate<String, OcupacionClase> kafkaTemplate;

    public void actualizarOcupacion(Long claseId, String claseNombre, int ocupacionActual, int capacidadMaxima) {
        OcupacionClase ocupacion = new OcupacionClase(claseId, claseNombre, ocupacionActual, capacidadMaxima, LocalDateTime.now());
        kafkaTemplate.send(TOPIC, String.valueOf(claseId), ocupacion);
    }
}
