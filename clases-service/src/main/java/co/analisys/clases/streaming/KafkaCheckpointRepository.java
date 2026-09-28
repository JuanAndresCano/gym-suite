package co.analisys.clases.streaming;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KafkaCheckpointRepository extends JpaRepository<KafkaCheckpoint, String> {
}
