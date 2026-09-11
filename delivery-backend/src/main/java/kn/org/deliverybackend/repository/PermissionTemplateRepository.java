package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.PermissionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionTemplateRepository extends JpaRepository<PermissionTemplate, UUID> {

    Optional<PermissionTemplate> findByNameIgnoreCase(String name);

    List<PermissionTemplate> findAllByOrderByNameAsc();
}
