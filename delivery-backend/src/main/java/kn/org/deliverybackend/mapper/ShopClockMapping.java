package kn.org.deliverybackend.mapper;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * How the mappers turn a moment ({@link Date}) into the date and time sent to
 * the admin and the shop, and back: on the server's clock, which is Bangladesh
 * time (see DeliveryBackendApplication). Without it MapStruct uses UTC, six
 * hours behind.
 */
@Component
public class ShopClockMapping {

    public LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public Date toDate(LocalDateTime local) {
        return local == null ? null : Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
    }
}
