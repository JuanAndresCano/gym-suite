package co.analisys.miembros.model;

import co.analisys.miembros.exception.ReglaNegocioException;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object.
 *
 * Se justifica como Value Object porque cumple los tres criterios: no tiene
 * identidad propia (dos correos con el mismo texto son el mismo correo), es
 * inmutable, y tiene una regla de validacion propia que le pertenece a el y no
 * al agregado que lo contiene.
 *
 * Es el unico Value Object del proyecto: los demas campos son datos simples sin
 * reglas asociadas, y envolverlos seria ceremonia sin valor.
 */
@Embeddable
public class Email {

    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    @Column(name = "email")
    private String valor;

    protected Email() {
        // Requerido por JPA
    }

    public Email(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("El email es obligatorio");
        }
        String normalizado = valor.trim().toLowerCase();
        if (!FORMATO.matcher(normalizado).matches()) {
            throw new ReglaNegocioException("El email '" + valor + "' no tiene un formato valido");
        }
        this.valor = normalizado;
    }

    /** Se serializa como texto plano, para no alterar el contrato del API. */
    @JsonValue
    public String getValor() {
        return valor;
    }

    public String getDominio() {
        return valor.substring(valor.indexOf('@') + 1);
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) return true;
        if (!(otro instanceof Email email)) return false;
        return Objects.equals(valor, email.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valor);
    }

    @Override
    public String toString() {
        return valor;
    }
}
