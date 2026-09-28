package co.analisys.entrenadores.service;

import co.analisys.entrenadores.dto.EntrenadorRequest;
import co.analisys.entrenadores.model.Entrenador;
import co.analisys.entrenadores.repository.EntrenadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EntrenadorService {
    @Autowired
    private EntrenadorRepository entrenadorRepository;

    public Entrenador agregarEntrenador(EntrenadorRequest request) {
        Entrenador entrenador = new Entrenador(request.getNombre(), request.getEspecialidad());
        return entrenadorRepository.save(entrenador);
    }

    public List<Entrenador> obtenerTodosEntrenadores() {
        return entrenadorRepository.findAll();
    }

    public Optional<Entrenador> obtenerEntrenadorPorId(Long id) {
        return entrenadorRepository.findById(id);
    }
}
