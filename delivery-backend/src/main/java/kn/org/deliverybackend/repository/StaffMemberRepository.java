package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    Optional<StaffMember> findByKeycloakUserId(String keycloakUserId);

    Optional<StaffMember> findByUsernameIgnoreCase(String username);

    Optional<StaffMember> findByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(StaffMember.Role role);

    List<StaffMember> findAllByOrderByActiveDescRoleAscFullNameAsc();
}
