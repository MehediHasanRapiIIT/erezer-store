package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.ContactReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContactReplyRepository extends JpaRepository<ContactReply, Long> {

    /** The replies to these messages, oldest first. */
    @Query("SELECT r FROM ContactReply r WHERE r.messageId IN :messageIds AND (r.deleted = false OR r.deleted IS NULL) ORDER BY r.id")
    List<ContactReply> findForMessages(@Param("messageIds") Collection<UUID> messageIds);
}
