package kn.org.deliverybackend.dto.staff;

import java.util.UUID;

/** One line of the activity log. {@code occurredAt} is business-local ("yyyy-MM-dd HH:mm:ss", Asia/Dhaka). */
public record ActivityEntryDTO(UUID id,
                               String occurredAt,
                               UUID staffId,
                               String staffName,
                               String staffUsername,
                               String method,
                               String path,
                               String permKey,
                               String area,
                               String targetId,
                               String summary,
                               Integer status,
                               /** Longer detail, e.g. every old and new price; null for most lines. */
                               String details) {}
