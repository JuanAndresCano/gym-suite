package co.analisys.entrenadores.controller;

import co.analisys.entrenadores.dto.EntrenadorRequest;
import co.analisys.entrenadores.model.Entrenador;
import co.analisys.entrenadores.service.EntrenadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entrenadores")
@Tag(name = "Entrenadores", description = "Gestión del personal entrenador y sus especialidades")
public class EntrenadorController {
    @Autowired
    private EntrenadorService entrenadorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un nuevo entrenador",
            description = "Registra un entrenador. Nombre y especialidad son obligatorios.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrenador agregado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre o especialidad faltantes)")
    })
    public Entrenador agregarEntrenador(@RequestBody EntrenadorRequest request) {
        return entrenadorService.agregarEntrenador(request);
    }

    @GetMapping
    @Operation(summary = "Listar todos los entrenadores")
    @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")
    public List<Entrenador> obtenerTodosEntrenadores() {
        return entrenadorService.obtenerTodosEntrenadores();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un entrenador por id",
            description = "Consultado también por clases-service para enriquecer sus clases con los datos del entrenador sin compartir base de datos entre ambos contextos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entrenador encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe un entrenador con ese id")
    })
    public ResponseEntity<Entrenador> obtenerEntrenadorPorId(@Parameter(description = "Id del entrenador") @PathVariable Long id) {
        return entrenadorService.obtenerEntrenadorPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
