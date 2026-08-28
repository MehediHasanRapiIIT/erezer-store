package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Addresses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressesRepository extends JpaRepository<Addresses, UUID> {
    @Query("SELECT a FROM Addresses a WHERE a.consumerId = :consumerId AND a.deleted = false")
    List<Addresses> findByConsumerId(UUID consumerId);

    @Query("SELECT COUNT(a) FROM Addresses a WHERE a.consumerId = :consumerId AND a.deleted = false")
    int countByConsumerId(UUID consumerId);

    @Query("SELECT a FROM Addresses a WHERE a.id = :id AND a.consumerId = :consumerId AND a.deleted = false")
    Optional<Addresses> findByIdAndConsumerId(UUID id, UUID consumerId);
}
