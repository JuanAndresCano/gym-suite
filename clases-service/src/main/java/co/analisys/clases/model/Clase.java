package co.analisys.clases.model;

import co.analisys.clases.exception.ReglaNegocioException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Aggregate Root del contexto acotado de Clases.
 *
 * Es el agregado con mas reglas del sistema, y el que justifica la frontera:
 *
 *  - Invariante de capacidad: el numero de inscritos nunca supera capacidadMaxima.
 *  - Invariante de unicidad: un miembro no puede inscribirse dos veces.
 *  - Una clase no puede programarse en el pasado ni sin entrenador asignado.
 *
 * La coleccion de inscritos vive DENTRO del agregado porque la regla de
 * capacidad solo puede garantizarse si el agregado controla ambos lados: si las
 * inscripciones fueran un agregado aparte, dos inscripciones simultaneas podrian
 * pasarse del cupo sin que nadie lo detecte.
 *
 * Referencia a Entrenador y a Miembro por identidad (Long), no por objeto:
 * pertenecen a otros contextos acotados, con otras bases de datos.
 */
@Entity
public class Clase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private LocalDateTime horario;
    private int capacidadMaxima;

    // En el monolito esto era "@ManyToOne private Entrenador entrenador".
    private Long entrenadorId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "clase_inscritos", joinColumns = @JoinColumn(name = "clase_id"))
    @Column(name = "miembro_id")
    private Set<Long> miembrosInscritos = new HashSet<>();

    protected Clase() {
        // Requerido por JPA
    }

    public Clase(String nombre, LocalDateTime horario, int capacidadMaxima, Long entrenadorId) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre de la clase es obligatorio");
        }
        if (horario == null) {
            throw new ReglaNegocioException("El horario de la clase es obligatorio");
        }
        if (horario.isBefore(LocalDateTime.now())) {
            throw new ReglaNegocioException("No se puede programar una clase en el pasado: " + horario);
        }
        if (capacidadMaxima <= 0) {
            throw new ReglaNegocioException("La capacidad maxima debe ser mayor que cero");
        }
        if (entrenadorId == null) {
            throw new ReglaNegocioException("La clase debe tener un entrenador asignado");
        }
        this.nombre = nombre.trim();
        this.horario = horario;
        this.capacidadMaxima = capacidadMaxima;
        this.entrenadorId = entrenadorId;
        this.miembrosInscritos = new HashSet<>();
    }

    /**
     * Inscribe un miembro. Es la operacion que da sentido al agregado: aqui es
     * donde se protege el invariante de capacidad.
     */
    public void inscribirMiembro(Long miembroId) {
        if (miembroId == null) {
            throw new ReglaNegocioException("El id del miembro es obligatorio");
        }
        if (miembrosInscritos.contains(miembroId)) {
            throw new ReglaNegocioException(
                    "El miembro " + miembroId + " ya esta inscrito en la clase " + nombre);
        }
        if (estaLlena()) {
            throw new ReglaNegocioException(
                    "La clase " + nombre + " ya alcanzo su capacidad maxima de " + capacidadMaxima);
        }
        miembrosInscritos.add(miembroId);
    }

    public void cancelarInscripcion(Long miembroId) {
        if (!miembrosInscritos.remove(miembroId)) {
            throw new ReglaNegocioException(
                    "El miembro " + miembroId + " no esta inscrito en la clase " + nombre);
        }
    }

    /** Reasigna el entrenador de la clase, referenciando por identidad. */
    public void asignarEntrenador(Long nuevoEntrenadorId) {
        if (nuevoEntrenadorId == null) {
            throw new ReglaNegocioException("La clase debe tener un entrenador asignado");
        }
        this.entrenadorId = nuevoEntrenadorId;
    }

    public boolean estaLlena() {
        return miembrosInscritos.size() >= capacidadMaxima;
    }

    public int getCuposDisponibles() {
        return capacidadMaxima - miembrosInscritos.size();
    }

    public int getTotalInscritos() {
        return miembrosInscritos.size();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public LocalDateTime getHorario() { return horario; }
    public int getCapacidadMaxima() { return capacidadMaxima; }
    public Long getEntrenadorId() { return entrenadorId; }

    public Set<Long> getMiembrosInscritos() {
        return Collections.unmodifiableSet(miembrosInscritos);
    }
}
