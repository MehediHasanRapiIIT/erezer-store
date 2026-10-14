package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.CustomerEmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerEmailSettingsRepository extends JpaRepository<CustomerEmailSettings, Long> {
}
