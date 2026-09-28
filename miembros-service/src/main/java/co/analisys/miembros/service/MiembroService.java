package co.analisys.miembros.service;

import co.analisys.miembros.dto.MiembroRequest;
import co.analisys.miembros.exception.RecursoNoEncontradoException;
import co.analisys.miembros.model.Miembro;
import co.analisys.miembros.repository.MiembroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MiembroService {
    @Autowired
    private MiembroRepository miembroRepository;

    public Miembro registrarMiembro(MiembroRequest request) {
        Miembro miembro = new Miembro(request.getNombre(), request.getEmail(), request.getFechaInscripcion());
        return miembroRepository.save(miembro);
    }

    public List<Miembro> obtenerTodosMiembros() {
        return miembroRepository.findAll();
    }

    public Miembro obtenerMiembro(Long id) {
        return miembroRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el miembro con id " + id));
    }
}
