package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.SizeFinderSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SizeFinderSettingRepository extends JpaRepository<SizeFinderSetting, String> {
}
