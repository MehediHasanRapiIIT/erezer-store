package kn.org.deliverybackend.dto.staff;

import java.util.List;
import java.util.UUID;

/** A saved set of permissions, e.g. "Order handler". {@code updatedAt} is business-local. */
public record PermissionTemplateDTO(UUID id, String name, List<String> permissions, String createdBy, String updatedAt) {}
