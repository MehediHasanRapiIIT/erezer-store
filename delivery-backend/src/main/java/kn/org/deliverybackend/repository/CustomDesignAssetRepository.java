package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.CustomDesignAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomDesignAssetRepository extends JpaRepository<CustomDesignAsset, UUID> {

    @Query("SELECT a FROM CustomDesignAsset a WHERE a.deleted = false AND a.active = true " +
            "ORDER BY a.sortOrder ASC, a.name ASC")
    List<CustomDesignAsset> findActive();

    @Query("SELECT a FROM CustomDesignAsset a WHERE a.deleted = false " +
            "ORDER BY a.sortOrder ASC, a.name ASC")
    List<CustomDesignAsset> findAllForAdmin();
}
