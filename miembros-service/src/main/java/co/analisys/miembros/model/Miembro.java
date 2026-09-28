package co.analisys.miembros.model;

import co.analisys.miembros.exception.ReglaNegocioException;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Aggregate Root del contexto acotado de Miembros.
 *
 * Invariantes: todo miembro tiene nombre y un Email valido, y su fecha de
 * inscripcion no puede estar en el futuro.
 */
@Entity
public class Miembro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;

    @Embedded
    private Email email;

    private LocalDate fechaInscripcion;

    protected Miembro() {
        // Requerido por JPA
    }

    public Miembro(String nombre, String email, LocalDate fechaInscripcion) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("El nombre del miembro es obligatorio");
        }
        LocalDate fecha = (fechaInscripcion != null) ? fechaInscripcion : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha de inscripcion no puede estar en el futuro");
        }
        this.nombre = nombre.trim();
        this.email = new Email(email);
        this.fechaInscripcion = fecha;
    }

    /** Cambia el correo del miembro. El Value Object valida el nuevo valor. */
    public void cambiarEmail(String nuevoEmail) {
        this.email = new Email(nuevoEmail);
    }

    public long antiguedadEnDias() {
        return ChronoUnit.DAYS.between(fechaInscripcion, LocalDate.now());
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public Email getEmail() { return email; }
    public LocalDate getFechaInscripcion() { return fechaInscripcion; }
}
