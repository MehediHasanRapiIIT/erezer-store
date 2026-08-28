package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Variant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VarientRepository extends JpaRepository<Variant, Long> {
}
