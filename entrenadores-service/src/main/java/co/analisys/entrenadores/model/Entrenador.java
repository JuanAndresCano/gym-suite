package co.analisys.entrenadores.model;

import co.analisys.entrenadores.exception.ReglaNegocioException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Aggregate Root del contexto acotado de Entrenadores.
 *
 * Invariante: todo entrenador tiene nombre y una especialidad, porque la
 * especialidad es lo que determina que clases puede dictar.
 */
@Entity
public class Entrenador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String especialidad;

    protected Entrenador() {
        // Requerido por JPA
    }

    public Entrenador(String nombre, String especialidad) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del entrenador es obligatorio");
        }
        if (especialidad == null || especialidad.isBlank()) {
            throw new ReglaNegocioException("La especialidad del entrenador es obligatoria");
        }
        this.nombre = nombre.trim();
        this.especialidad = especialidad.trim();
    }

    /** Reglas de negocio del contexto: un entrenador solo dicta su especialidad. */
    public boolean puedeDictar(String especialidadRequerida) {
        return especialidad.equalsIgnoreCase(especialidadRequerida);
    }

    public void cambiarEspecialidad(String nuevaEspecialidad) {
        if (nuevaEspecialidad == null || nuevaEspecialidad.isBlank()) {
            throw new ReglaNegocioException("La especialidad del entrenador es obligatoria");
        }
        this.especialidad = nuevaEspecialidad.trim();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEspecialidad() { return especialidad; }
}
