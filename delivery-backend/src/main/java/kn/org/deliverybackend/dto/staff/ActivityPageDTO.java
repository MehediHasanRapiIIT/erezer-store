package kn.org.deliverybackend.dto.staff;

import java.util.List;

public record ActivityPageDTO(List<ActivityEntryDTO> items, long total, int page, int size) {}
