package co.analisys.clases.controller;

import co.analisys.clases.dto.ClaseDTO;
import co.analisys.clases.dto.ClaseRequest;
import co.analisys.clases.dto.InscripcionRequest;
import co.analisys.clases.model.Clase;
import co.analisys.clases.service.ClaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clases")
@Tag(name = "Clases", description = "Programación de clases, horarios e inscripciones")
public class ClaseController {
    @Autowired
    private ClaseService claseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Programar una nueva clase",
            description = "Crea una clase. No se permite programar en el pasado, la capacidad debe ser mayor a 0 y el entrenador es obligatorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Clase programada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (horario en el pasado, capacidad <= 0, entrenador faltante)")
    })
    public Clase programarClase(@RequestBody ClaseRequest request) {
        return claseService.programarClase(request);
    }

    @GetMapping
    @Operation(summary = "Listar todas las clases")
    @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")
    public List<ClaseDTO> obtenerTodasClases() {
        return claseService.obtenerTodasClases();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una clase por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clase encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe una clase con ese id")
    })
    public ClaseDTO obtenerClase(@Parameter(description = "Id de la clase") @PathVariable Long id) {
        return claseService.obtenerClase(id);
    }

    @PostMapping("/{id}/inscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inscribir un miembro en la clase",
            description = "Inscribe un miembro en la clase. El agregado rechaza la operación si ya no hay cupo disponible o si el miembro ya está inscrito.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Miembro inscrito exitosamente"),
            @ApiResponse(responseCode = "400", description = "Sin cupo disponible o miembro ya inscrito"),
            @ApiResponse(responseCode = "404", description = "No existe una clase con ese id")
    })
    public ClaseDTO inscribirMiembro(@Parameter(description = "Id de la clase") @PathVariable Long id,
                                      @RequestBody InscripcionRequest request) {
        return claseService.inscribirMiembro(id, request.getMiembroId());
    }

    @DeleteMapping("/{id}/inscripciones/{miembroId}")
    @Operation(summary = "Cancelar la inscripción de un miembro en una clase")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inscripción cancelada exitosamente"),
            @ApiResponse(responseCode = "404", description = "No existe la clase o el miembro no estaba inscrito")
    })
    public ClaseDTO cancelarInscripcion(@Parameter(description = "Id de la clase") @PathVariable Long id,
                                         @Parameter(description = "Id del miembro") @PathVariable Long miembroId) {
        return claseService.cancelarInscripcion(id, miembroId);
    }
}
