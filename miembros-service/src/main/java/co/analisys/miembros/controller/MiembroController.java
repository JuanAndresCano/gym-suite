package co.analisys.miembros.controller;

import co.analisys.miembros.dto.MiembroRequest;
import co.analisys.miembros.model.Miembro;
import co.analisys.miembros.service.MiembroService;
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
@RequestMapping("/api/miembros")
@Tag(name = "Miembros", description = "Inscripción y consulta de los miembros del gimnasio")
public class MiembroController {
    @Autowired
    private MiembroService miembroService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un nuevo miembro",
            description = "Inscribe un miembro nuevo. La fecha de inscripción no puede estar en el futuro y el email debe tener formato válido.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Miembro registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (email mal formado, nombre faltante, etc.)")
    })
    public Miembro registrarMiembro(@RequestBody MiembroRequest request) {
        return miembroService.registrarMiembro(request);
    }

    @GetMapping
    @Operation(summary = "Listar todos los miembros")
    @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")
    public List<Miembro> obtenerTodosMiembros() {
        return miembroService.obtenerTodosMiembros();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un miembro por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Miembro encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe un miembro con ese id")
    })
    public Miembro obtenerMiembro(@Parameter(description = "Id del miembro") @PathVariable Long id) {
        return miembroService.obtenerMiembro(id);
    }
}
