package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.StaffMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    Optional<StaffMember> findByKeycloakUserId(String keycloakUserId);

    Optional<StaffMember> findByUsernameIgnoreCase(String username);

    Optional<StaffMember> findByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(StaffMember.Role role);

    /**
     * The Staff page: active people first, then admins before moderators, then by name.
     * {@code q} is a lower-case "%text%" pattern (or null) matched against the full name,
     * username and email.
     */
    String ADMIN_FILTERS = "(:q IS NULL OR LOWER(s.fullName) LIKE :q ESCAPE '\\' " +
            "  OR LOWER(s.username) LIKE :q ESCAPE '\\' OR LOWER(s.email) LIKE :q ESCAPE '\\') ";

    @Query(value = "SELECT s FROM StaffMember s WHERE " + ADMIN_FILTERS +
            "ORDER BY s.active DESC, s.role ASC, s.fullName ASC, s.id",
            countQuery = "SELECT COUNT(s) FROM StaffMember s WHERE " + ADMIN_FILTERS)
    Page<StaffMember> findForAdmin(@Param("q") String q, Pageable pageable);
}
