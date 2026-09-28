package co.analisys.entrenadores;

import co.analisys.entrenadores.model.Entrenador;
import co.analisys.entrenadores.repository.EntrenadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Carga los datos de ejemplo del monolito original correspondientes a este
 * contexto acotado, para que el servicio arranque con informacion util.
 */
@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private EntrenadorRepository entrenadorRepository;

    @Override
    public void run(String... args) {
        if (entrenadorRepository.count() > 0) {
            return;
        }
        entrenadorRepository.save(new Entrenador("Carlos Rodriguez", "Yoga"));
        entrenadorRepository.save(new Entrenador("Ana Martinez", "Spinning"));
        System.out.println("entrenadores-service: datos de ejemplo cargados.");
    }
}
