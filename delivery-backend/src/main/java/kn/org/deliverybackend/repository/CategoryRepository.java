package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c WHERE c.deleted = false AND " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY c.name ASC")
    Page<Category> searchAdmin(@Param("q") String q, Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.deleted = false AND c.isActive = true AND " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY c.name ASC")
    Page<Category> searchCustomer(@Param("q") String q, Pageable pageable);

    /** Resolves a storefront URL like /erezer-pink to its category. */
    Optional<Category> findBySlugIgnoreCaseAndDeletedFalse(String slug);

    /** Guards slug uniqueness on create/update. */
    Optional<Category> findBySlugIgnoreCaseAndDeletedFalseAndIdNot(String slug, Long id);

    /** Categories the admin promoted to their own landing-page section. */
    List<Category> findByShowOnHomeTrueAndIsActiveTrueAndDeletedFalseOrderByHomeSortOrderAscNameAsc();
}
