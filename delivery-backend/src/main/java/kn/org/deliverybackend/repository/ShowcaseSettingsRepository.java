package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ShowcaseSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShowcaseSettingsRepository extends JpaRepository<ShowcaseSettings, Long> {
}
