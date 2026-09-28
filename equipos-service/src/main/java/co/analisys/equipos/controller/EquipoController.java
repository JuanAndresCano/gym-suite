package co.analisys.equipos.controller;

import co.analisys.equipos.dto.AjusteInventarioRequest;
import co.analisys.equipos.dto.EquipoRequest;
import co.analisys.equipos.model.Equipo;
import co.analisys.equipos.service.EquipoService;
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
@RequestMapping("/api/equipos")
@Tag(name = "Equipos", description = "Gestión del inventario de equipos del gimnasio")
public class EquipoController {
    @Autowired
    private EquipoService equipoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un nuevo equipo al inventario",
            description = "Da de alta un equipo. El nombre es obligatorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipo agregado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre faltante)")
    })
    public Equipo agregarEquipo(@RequestBody EquipoRequest request) {
        return equipoService.agregarEquipo(request);
    }

    @GetMapping
    @Operation(summary = "Listar todos los equipos")
    @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")
    public List<Equipo> obtenerTodosEquipos() {
        return equipoService.obtenerTodosEquipos();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un equipo por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe un equipo con ese id")
    })
    public Equipo obtenerEquipo(@Parameter(description = "Id del equipo") @PathVariable Long id) {
        return equipoService.obtenerEquipo(id);
    }

    @PatchMapping("/{id}/inventario")
    @Operation(summary = "Ajustar el inventario de un equipo",
            description = "Un ajuste positivo ingresa unidades, uno negativo las retira. El inventario nunca puede quedar en negativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventario ajustado exitosamente"),
            @ApiResponse(responseCode = "400", description = "El ajuste dejaría el inventario en negativo"),
            @ApiResponse(responseCode = "404", description = "No existe un equipo con ese id")
    })
    public Equipo ajustarInventario(@Parameter(description = "Id del equipo") @PathVariable Long id,
                                     @RequestBody AjusteInventarioRequest request) {
        return equipoService.ajustarInventario(id, request.getAjuste());
    }
}
