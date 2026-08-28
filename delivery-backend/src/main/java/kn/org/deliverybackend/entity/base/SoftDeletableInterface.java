package kn.org.deliverybackend.entity.base;

import java.util.Date;

public interface SoftDeletableInterface {

    Boolean getDeleted();

    void setDeleted(Boolean deleted);

    Long getDeletedBy();

    void setDeletedBy(Long deletedBy);

    Date getDeletedAt();

    void setDeletedAt(Date deletedAt);
}
