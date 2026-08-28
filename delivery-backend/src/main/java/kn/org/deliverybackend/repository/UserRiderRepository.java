package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.UserRider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRiderRepository extends JpaRepository<UserRider, UUID> {

    @Query("SELECT r FROM UserRider r WHERE r.deleted = false AND (" +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(r.contactPhone) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(r.contactNo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(r.plateNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(r.status) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY r.createdAt DESC")
    Page<UserRider> searchAdmin(@Param("q") String q, Pageable pageable);
}
