package co.analisys.equipos.model;

import co.analisys.equipos.exception.ReglaNegocioException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Aggregate Root del contexto acotado de Equipos.
 *
 * Protege dos invariantes: todo equipo tiene nombre, y el inventario nunca
 * puede quedar en negativo. No expone setters: el estado solo cambia a traves
 * de operaciones de negocio que validan antes de mutar.
 */
@Entity
public class Equipo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String descripcion;
    private int cantidad;

    protected Equipo() {
        // Requerido por JPA
    }

    public Equipo(String nombre, String descripcion, int cantidad) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del equipo es obligatorio");
        }
        if (cantidad < 0) {
            throw new ReglaNegocioException("La cantidad no puede ser negativa");
        }
        this.nombre = nombre.trim();
        this.descripcion = descripcion;
        this.cantidad = cantidad;
    }

    /** Retira unidades del inventario. Invariante: el stock nunca queda negativo. */
    public void retirarUnidades(int unidades) {
        if (unidades <= 0) {
            throw new ReglaNegocioException("Las unidades a retirar deben ser mayores que cero");
        }
        if (unidades > cantidad) {
            throw new ReglaNegocioException(
                    "No hay suficientes unidades de " + nombre
                    + ": disponibles " + cantidad + ", solicitadas " + unidades);
        }
        this.cantidad -= unidades;
    }

    /** Ingresa unidades al inventario. */
    public void agregarUnidades(int unidades) {
        if (unidades <= 0) {
            throw new ReglaNegocioException("Las unidades a agregar deben ser mayores que cero");
        }
        this.cantidad += unidades;
    }

    public boolean hayDisponibilidad(int unidades) {
        return unidades > 0 && unidades <= cantidad;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public int getCantidad() { return cantidad; }
}
