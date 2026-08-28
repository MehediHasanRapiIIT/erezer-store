package kn.org.deliverybackend.entity.base;

import java.util.Date;

public interface AuditableInterface {

    Long getCreatedBy();

    void setCreatedBy(Long createdBy);

    Date getCreatedAt();

    void setCreatedAt(Date createdAt);

    Long getUpdatedBy();

    void setUpdatedBy(Long updatedBy);

    Date getUpdatedAt();

    void setUpdatedAt(Date updatedAt);
}
