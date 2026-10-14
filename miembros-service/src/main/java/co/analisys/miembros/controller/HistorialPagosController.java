package co.analisys.miembros.controller;

import co.analisys.miembros.model.Pago;
import co.analisys.miembros.repository.PagoRepository;
import co.analisys.miembros.service.MiembroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/miembros/{miembroId}/pagos")
@Tag(name = "Pagos", description = "Procesamiento asincrónico de pagos con manejo de fallos vía Dead Letter Queue")
public class HistorialPagosController {

    private final PagoRepository pagoRepository;
    private final MiembroService miembroService;

    public HistorialPagosController(PagoRepository pagoRepository, MiembroService miembroService) {
        this.pagoRepository = pagoRepository;
        this.miembroService = miembroService;
    }

    @GetMapping
    @Operation(summary = "Historial de pagos de un miembro",
            description = "Lista los pagos procesados con éxito, del más reciente al más antiguo. Los pagos fallidos (enviados a la DLQ) no aparecen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "No existe un miembro con ese id")
    })
    public List<Pago> historialDePagos(@Parameter(description = "Id del miembro") @PathVariable Long miembroId) {
        miembroService.obtenerMiembro(miembroId);
        return pagoRepository.findByMiembroIdOrderByFechaDesc(miembroId);
    }
}
