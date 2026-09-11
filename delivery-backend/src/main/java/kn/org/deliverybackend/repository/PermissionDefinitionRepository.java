package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.PermissionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, String> {

    List<PermissionDefinition> findByActiveTrueOrderBySortOrderAsc();
}
