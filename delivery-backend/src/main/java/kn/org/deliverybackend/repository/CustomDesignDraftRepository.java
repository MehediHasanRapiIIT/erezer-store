package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.CustomDesignDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomDesignDraftRepository extends JpaRepository<CustomDesignDraft, UUID> {

    @Query("SELECT d FROM CustomDesignDraft d WHERE d.deleted = false AND d.userId = :userId " +
            "ORDER BY d.updatedAt DESC")
    List<CustomDesignDraft> findByUser(@Param("userId") UUID userId);

    @Query("SELECT d FROM CustomDesignDraft d WHERE d.deleted = false AND d.id = :id AND d.userId = :userId")
    Optional<CustomDesignDraft> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT d FROM CustomDesignDraft d WHERE d.deleted = false AND d.shareToken = :token")
    Optional<CustomDesignDraft> findByShareToken(@Param("token") String token);
}
