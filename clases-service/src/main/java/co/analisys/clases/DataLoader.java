package co.analisys.clases;

import co.analisys.clases.model.Clase;
import co.analisys.clases.repository.ClaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Carga los datos de ejemplo del monolito original correspondientes a este
 * contexto acotado. Los entrenadores se referencian por identidad: los ids 1 y 2
 * son los que entrenadores-service crea en su propio DataLoader.
 */
@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private ClaseRepository claseRepository;

    @Override
    public void run(String... args) {
        if (claseRepository.count() > 0) {
            return;
        }
        claseRepository.save(new Clase(
                "Yoga Matutino",
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
                20,
                1L));
        claseRepository.save(new Clase(
                "Spinning Vespertino",
                LocalDateTime.now().plusDays(1).withHour(18).withMinute(0),
                15,
                2L));
        System.out.println("clases-service: datos de ejemplo cargados.");
    }
}
