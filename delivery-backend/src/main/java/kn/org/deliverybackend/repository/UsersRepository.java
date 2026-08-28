package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Users;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsersRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByPhoneNumber(String phoneNumber);

    Optional<Users> findByEmail(String email);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID id);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByEmail(String email);

    Optional<Users> findByEmailVerificationToken(String token);

    Optional<Users> findByPasswordResetToken(String token);

    @Query("SELECT u FROM Users u WHERE u.deleted = false AND (" +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY u.createdAt DESC")
    Page<Users> searchAdmin(@Param("q") String q, Pageable pageable);
}
