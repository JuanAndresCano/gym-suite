package co.analisys.miembros.service;

import co.analisys.miembros.exception.PagoFallidoException;
import co.analisys.miembros.messaging.PagoMessage;
import co.analisys.miembros.model.Pago;
import co.analisys.miembros.repository.PagoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PagoServiceTest {

    private final PagoRepository repository = mock(PagoRepository.class);
    private final PagoService service = new PagoService(repository);

    @Test
    void pagoExitosoSeGuarda() {
        service.procesarPago(new PagoMessage("p-1", 7L, 50000, "Mensualidad"));

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(repository).save(captor.capture());
        Pago guardado = captor.getValue();
        assertThat(guardado.getId()).isEqualTo("p-1");
        assertThat(guardado.getMiembroId()).isEqualTo(7L);
        assertThat(guardado.getMonto()).isEqualTo(50000);
        assertThat(guardado.getConcepto()).isEqualTo("Mensualidad");
        assertThat(guardado.getFecha()).isNotNull();
    }

    @Test
    void pagoFallidoLanzaExcepcionYNoSeGuarda() {
        assertThatThrownBy(() -> service.procesarPago(new PagoMessage("p-2", 7L, 0, "Invalido")))
                .isInstanceOf(PagoFallidoException.class);
        verify(repository, never()).save(any());
    }
}
