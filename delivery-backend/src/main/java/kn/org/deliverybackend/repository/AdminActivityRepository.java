package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.AdminActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** The activity log. Filtered searches go through {@link JpaSpecificationExecutor}. */
public interface AdminActivityRepository extends JpaRepository<AdminActivity, UUID>, JpaSpecificationExecutor<AdminActivity> {

    /**
     * Everyone who appears in the log, one row per person: [staff id as text,
     * a recorded name]. Includes people who have since been deleted.
     */
    @Query(value = """
            SELECT CAST(staff_id AS varchar), max(staff_name)
            FROM admin_activity
            WHERE staff_id IS NOT NULL
            GROUP BY staff_id
            ORDER BY max(staff_name)
            """, nativeQuery = true)
    List<Object[]> people();
}
