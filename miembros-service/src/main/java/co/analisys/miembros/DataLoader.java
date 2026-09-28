package co.analisys.miembros;

import co.analisys.miembros.model.Miembro;
import co.analisys.miembros.repository.MiembroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Carga los datos de ejemplo del monolito original correspondientes a este
 * contexto acotado, para que el servicio arranque con informacion util.
 */
@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private MiembroRepository miembroRepository;

    @Override
    public void run(String... args) {
        if (miembroRepository.count() > 0) {
            return;
        }
        miembroRepository.save(new Miembro("Juan Perez", "juan@email.com", LocalDate.now()));
        miembroRepository.save(new Miembro("Maria Lopez", "maria@email.com", LocalDate.now().minusDays(30)));
        System.out.println("miembros-service: datos de ejemplo cargados.");
    }
}
