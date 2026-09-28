package co.analisys.clases.client;

import co.analisys.clases.dto.EntrenadorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Cliente REST hacia entrenadores-service (contexto acotado de Entrenadores).
 * Si el otro servicio esta caido o el entrenador no existe, devuelve vacio en
 * lugar de propagar el error: el contexto de Clases sigue siendo funcional por
 * si solo, la informacion del entrenador es un enriquecimiento opcional.
 */
@Component
public class EntrenadorClient {

    private static final Logger log = LoggerFactory.getLogger(EntrenadorClient.class);

    private final RestTemplate restTemplate;
    private final String entrenadoresServiceUrl;

    public EntrenadorClient(RestTemplate restTemplate,
                            @Value("${entrenadores.service.url}") String entrenadoresServiceUrl) {
        this.restTemplate = restTemplate;
        this.entrenadoresServiceUrl = entrenadoresServiceUrl;
    }

    public Optional<EntrenadorDTO> obtenerEntrenador(Long entrenadorId) {
        if (entrenadorId == null) {
            return Optional.empty();
        }
        try {
            EntrenadorDTO entrenador = restTemplate.getForObject(
                    entrenadoresServiceUrl + "/api/entrenadores/{id}",
                    EntrenadorDTO.class,
                    entrenadorId);
            return Optional.ofNullable(entrenador);
        } catch (RestClientException e) {
            log.warn("No se pudo obtener el entrenador {}: {}", entrenadorId, e.getMessage());
            return Optional.empty();
        }
    }
}
