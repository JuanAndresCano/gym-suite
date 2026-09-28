package co.analisys.clases.service;

import co.analisys.clases.client.EntrenadorClient;
import co.analisys.clases.config.RabbitMQConfig;
import co.analisys.clases.dto.ClaseDTO;
import co.analisys.clases.dto.ClaseRequest;
import co.analisys.clases.dto.EntrenadorDTO;
import co.analisys.clases.exception.RecursoNoEncontradoException;
import co.analisys.clases.messaging.CambioHorarioEvent;
import co.analisys.clases.messaging.InscripcionNotificacion;
import co.analisys.clases.model.Clase;
import co.analisys.clases.repository.ClaseRepository;
import co.analisys.clases.streaming.OcupacionClaseProducer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ClaseService {
    @Autowired
    private ClaseRepository claseRepository;

    @Autowired
    private EntrenadorClient entrenadorClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OcupacionClaseProducer ocupacionClaseProducer;

    public Clase programarClase(ClaseRequest request) {
        Clase clase = new Clase(
                request.getNombre(),
                request.getHorario(),
                request.getCapacidadMaxima(),
                request.getEntrenadorId());
        return claseRepository.save(clase);
    }

    public List<ClaseDTO> obtenerTodasClases() {
        return claseRepository.findAll().stream()
                .map(this::aDTOConEntrenador)
                .toList();
    }

    public ClaseDTO obtenerClase(Long id) {
        return aDTOConEntrenador(buscarClase(id));
    }

    /**
     * Inscribe un miembro delegando la decision al agregado: es Clase quien
     * conoce y protege la regla de capacidad, no este servicio.
     */
    @Transactional
    public ClaseDTO inscribirMiembro(Long claseId, Long miembroId) {
        Clase clase = buscarClase(claseId);
        clase.inscribirMiembro(miembroId);
        Clase guardada = claseRepository.save(clase);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICACIONES_INSCRIPCION_QUEUE,
                new InscripcionNotificacion(miembroId, guardada.getId(), guardada.getNombre(), guardada.getHorario()));
        ocupacionClaseProducer.actualizarOcupacion(
                guardada.getId(), guardada.getNombre(), guardada.getTotalInscritos(), guardada.getCapacidadMaxima());

        return aDTOConEntrenador(guardada);
    }

    @Transactional
    public ClaseDTO cancelarInscripcion(Long claseId, Long miembroId) {
        Clase clase = buscarClase(claseId);
        clase.cancelarInscripcion(miembroId);
        Clase guardada = claseRepository.save(clase);

        ocupacionClaseProducer.actualizarOcupacion(
                guardada.getId(), guardada.getNombre(), guardada.getTotalInscritos(), guardada.getCapacidadMaxima());

        return aDTOConEntrenador(guardada);
    }

    /**
     * Reprograma el horario de una clase y publica el cambio al exchange fanout
     * "horarios.exchange": todos los suscriptores (miembros y entrenadores)
     * reciben la misma notificación de forma independiente.
     */
    @Transactional
    public ClaseDTO reprogramarClase(Long claseId, LocalDateTime nuevoHorario) {
        Clase clase = buscarClase(claseId);
        LocalDateTime horarioAnterior = clase.getHorario();
        clase.reprogramar(nuevoHorario);
        Clase guardada = claseRepository.save(clase);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.HORARIOS_EXCHANGE,
                "",
                new CambioHorarioEvent(guardada.getId(), guardada.getNombre(), horarioAnterior, guardada.getHorario()));

        return aDTOConEntrenador(guardada);
    }

    private Clase buscarClase(Long id) {
        return claseRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la clase con id " + id));
    }

    private ClaseDTO aDTOConEntrenador(Clase clase) {
        ClaseDTO dto = new ClaseDTO();
        dto.setId(clase.getId());
        dto.setNombre(clase.getNombre());
        dto.setHorario(clase.getHorario());
        dto.setCapacidadMaxima(clase.getCapacidadMaxima());
        dto.setEntrenadorId(clase.getEntrenadorId());
        dto.setTotalInscritos(clase.getTotalInscritos());
        dto.setCuposDisponibles(clase.getCuposDisponibles());
        dto.setMiembrosInscritos(clase.getMiembrosInscritos());

        Optional<EntrenadorDTO> entrenador = entrenadorClient.obtenerEntrenador(clase.getEntrenadorId());
        dto.setEntrenadorNombre(entrenador.map(EntrenadorDTO::getNombre).orElse("No disponible"));
        dto.setEntrenadorEspecialidad(entrenador.map(EntrenadorDTO::getEspecialidad).orElse("No disponible"));
        return dto;
    }
}
