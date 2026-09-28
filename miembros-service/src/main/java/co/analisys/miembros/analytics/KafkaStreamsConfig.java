package co.analisys.miembros.analytics;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;

/**
 * Analiza los datos de entrenamiento de los miembros con Kafka Streams: agrega
 * las sesiones de cada miembro en ventanas de 7 días y publica el resumen.
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    public static final String DATOS_ENTRENAMIENTO_TOPIC = "datos-entrenamiento";
    public static final String RESUMEN_ENTRENAMIENTO_TOPIC = "resumen-entrenamiento";

    @Bean
    public NewTopic datosEntrenamientoTopic() {
        return TopicBuilder.name(DATOS_ENTRENAMIENTO_TOPIC)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000") // 14 días
                .build();
    }

    @Bean
    public NewTopic resumenEntrenamientoTopic() {
        return TopicBuilder.name(RESUMEN_ENTRENAMIENTO_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public KStream<String, DatosEntrenamiento> kStream(StreamsBuilder streamsBuilder) {
        JsonSerde<DatosEntrenamiento> datoSerde = new JsonSerde<>(DatosEntrenamiento.class).ignoreTypeHeaders();
        JsonSerde<ResumenEntrenamiento> resumenSerde = new JsonSerde<>(ResumenEntrenamiento.class).ignoreTypeHeaders();

        KStream<String, DatosEntrenamiento> stream = streamsBuilder.stream(
                DATOS_ENTRENAMIENTO_TOPIC, Consumed.with(Serdes.String(), datoSerde));

        stream
                .groupByKey(Grouped.with(Serdes.String(), datoSerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofDays(7)))
                .aggregate(
                        ResumenEntrenamiento::new,
                        (key, value, aggregate) -> aggregate.actualizar(value),
                        Materialized.<String, ResumenEntrenamiento, WindowStore<Bytes, byte[]>>as("resumen-entrenamiento-store")
                                .withValueSerde(resumenSerde))
                .toStream()
                .map((windowedKey, resumen) -> KeyValue.pair(windowedKey.key(), resumen))
                .to(RESUMEN_ENTRENAMIENTO_TOPIC, Produced.with(Serdes.String(), resumenSerde));

        return stream;
    }
}
