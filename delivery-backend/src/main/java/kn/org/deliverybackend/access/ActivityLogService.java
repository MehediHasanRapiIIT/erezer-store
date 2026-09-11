package kn.org.deliverybackend.access;

import jakarta.persistence.criteria.Predicate;
import kn.org.deliverybackend.dto.staff.ActivityEntryDTO;
import kn.org.deliverybackend.dto.staff.ActivityPageDTO;
import kn.org.deliverybackend.dto.staff.ActivityPersonDTO;
import kn.org.deliverybackend.entity.AdminActivity;
import kn.org.deliverybackend.reporting.BusinessCalendar;
import kn.org.deliverybackend.repository.AdminActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads the activity log for the Activity log page. Dates are business days
 * in the shop's time zone, so "today" means today in Dhaka.
 */
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 200;

    private final AdminActivityRepository repository;
    private final BusinessCalendar calendar;

    /** Newest first. Every filter is optional; {@code from} and {@code to} are inclusive. */
    @Transactional(readOnly = true)
    public ActivityPageDTO search(UUID staffId, String area, LocalDate from, LocalDate to, int page, int size) {
        Specification<AdminActivity> filter = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (staffId != null) where.add(cb.equal(root.get("staffId"), staffId));
            if (area != null && !area.isBlank()) where.add(cb.equal(root.get("area"), area.trim()));
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("occurredAt"), calendar.toUtc(from)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.<LocalDateTime>get("occurredAt"), calendar.toUtc(to.plusDays(1))));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AdminActivity> result = repository.findAll(filter, request);
        return new ActivityPageDTO(result.getContent().stream().map(this::toDTO).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public List<ActivityPersonDTO> people() {
        return repository.people().stream()
                .map(row -> new ActivityPersonDTO(UUID.fromString(row[0].toString()), (String) row[1]))
                .toList();
    }

    private ActivityEntryDTO toDTO(AdminActivity a) {
        return new ActivityEntryDTO(a.getId(), SHOWN.format(calendar.fromUtc(a.getOccurredAt())),
                a.getStaffId(), a.getStaffName(), a.getStaffUsername(), a.getMethod(), a.getPath(),
                a.getPermKey(), a.getArea(), a.getTargetId(), a.getSummary(), a.getStatus(), a.getDetails());
    }
}
