package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ContentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentPageRepository extends JpaRepository<ContentPage, Long> {

    /** Every page that has not been deleted, in the order the shop gave them. */
    @Query("SELECT p FROM ContentPage p WHERE p.deleted = false OR p.deleted IS NULL ORDER BY p.sortOrder, p.id")
    List<ContentPage> findLive();

    @Query("SELECT p FROM ContentPage p WHERE p.id = :id AND (p.deleted = false OR p.deleted IS NULL)")
    Optional<ContentPage> findLive(@Param("id") Long id);

    @Query("SELECT p FROM ContentPage p WHERE LOWER(p.slug) = LOWER(:slug) AND (p.deleted = false OR p.deleted IS NULL)")
    Optional<ContentPage> findLiveBySlug(@Param("slug") String slug);
}
