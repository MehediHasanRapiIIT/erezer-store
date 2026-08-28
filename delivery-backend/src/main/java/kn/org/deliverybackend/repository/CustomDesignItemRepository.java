package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.CustomDesignItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomDesignItemRepository extends JpaRepository<CustomDesignItem, UUID> {

    @Query("SELECT i FROM CustomDesignItem i WHERE i.deleted = false AND i.active = true " +
            "ORDER BY i.sortOrder ASC, i.name ASC")
    List<CustomDesignItem> findActive();

    @Query("SELECT i FROM CustomDesignItem i WHERE i.deleted = false " +
            "ORDER BY i.sortOrder ASC, i.name ASC")
    List<CustomDesignItem> findAllForAdmin();
}
