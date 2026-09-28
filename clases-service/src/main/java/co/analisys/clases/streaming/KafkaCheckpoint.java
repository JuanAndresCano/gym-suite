package co.analisys.clases.streaming;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Registra el último offset procesado exitosamente por topic+partición, en una
 * base de datos transaccional (H2). Es el mecanismo de recuperación ante fallos:
 * si el consumidor se cae y reinicia, retoma justo después del último checkpoint
 * en lugar de reprocesar todo el log desde el principio o perder mensajes.
 */
@Entity
public class KafkaCheckpoint {
    @Id
    @Column(name = "topic_partition")
    private String topicPartition;
    private long lastOffset;

    protected KafkaCheckpoint() {
    }

    public KafkaCheckpoint(String topicPartition, long lastOffset) {
        this.topicPartition = topicPartition;
        this.lastOffset = lastOffset;
    }

    public static String key(String topic, int partition) {
        return topic + "-" + partition;
    }

    public String getTopicPartition() { return topicPartition; }
    public long getLastOffset() { return lastOffset; }
    public void setLastOffset(long lastOffset) { this.lastOffset = lastOffset; }
}
