package co.analisys.miembros.repository;

import co.analisys.miembros.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, String> {
    List<Pago> findByMiembroIdOrderByFechaDesc(Long miembroId);
}
