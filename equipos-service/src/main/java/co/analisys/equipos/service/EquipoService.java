package co.analisys.equipos.service;

import co.analisys.equipos.dto.EquipoRequest;
import co.analisys.equipos.exception.RecursoNoEncontradoException;
import co.analisys.equipos.model.Equipo;
import co.analisys.equipos.repository.EquipoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EquipoService {
    @Autowired
    private EquipoRepository equipoRepository;

    public Equipo agregarEquipo(EquipoRequest request) {
        Equipo equipo = new Equipo(request.getNombre(), request.getDescripcion(), request.getCantidad());
        return equipoRepository.save(equipo);
    }

    public List<Equipo> obtenerTodosEquipos() {
        return equipoRepository.findAll();
    }

    public Equipo obtenerEquipo(Long id) {
        return equipoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el equipo con id " + id));
    }

    /**
     * Ajusta el inventario delegando la decision al agregado, que es quien
     * conoce la regla de que el stock no puede quedar negativo.
     */
    @Transactional
    public Equipo ajustarInventario(Long id, int ajuste) {
        Equipo equipo = obtenerEquipo(id);
        if (ajuste < 0) {
            equipo.retirarUnidades(-ajuste);
        } else {
            equipo.agregarUnidades(ajuste);
        }
        return equipoRepository.save(equipo);
    }
}
