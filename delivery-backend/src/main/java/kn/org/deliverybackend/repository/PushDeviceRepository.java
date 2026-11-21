package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, String> {

    List<PushDevice> findByUserId(UUID userId);
}
