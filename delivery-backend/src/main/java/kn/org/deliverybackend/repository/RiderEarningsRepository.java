package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.RiderEarnings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RiderEarningsRepository extends JpaRepository<RiderEarnings, UUID> {
}
