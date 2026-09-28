package co.analisys.miembros.exception;

/**
 * Se lanza cuando una operacion violaria un invariante del agregado.
 * El agregado se protege a si mismo: nunca queda en un estado invalido.
 */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
