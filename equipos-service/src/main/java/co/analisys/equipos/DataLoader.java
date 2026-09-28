package co.analisys.equipos;

import co.analisys.equipos.model.Equipo;
import co.analisys.equipos.repository.EquipoRepository;
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
    private EquipoRepository equipoRepository;

    @Override
    public void run(String... args) {
        if (equipoRepository.count() > 0) {
            return;
        }
        equipoRepository.save(new Equipo("Mancuernas", "Set de mancuernas de 5kg", 20));
        equipoRepository.save(new Equipo("Bicicleta estatica", "Bicicleta para spinning", 15));
        System.out.println("equipos-service: datos de ejemplo cargados.");
    }
}
