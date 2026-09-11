package kn.org.deliverybackend.dto.staff;

import java.util.UUID;

/** Someone who appears in the activity log, for the "person" filter. Includes people since deleted. */
public record ActivityPersonDTO(UUID staffId, String name) {}
