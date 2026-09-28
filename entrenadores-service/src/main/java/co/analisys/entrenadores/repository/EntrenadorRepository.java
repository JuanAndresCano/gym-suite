package co.analisys.entrenadores.repository;

import co.analisys.entrenadores.model.Entrenador;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntrenadorRepository extends JpaRepository<Entrenador, Long> {
}
