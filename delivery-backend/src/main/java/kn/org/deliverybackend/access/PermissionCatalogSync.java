package kn.org.deliverybackend.access;

import kn.org.deliverybackend.entity.PermissionDefinition;
import kn.org.deliverybackend.repository.PermissionDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes the permission list from {@link Perm} into the database at startup.
 * New permissions appear on the Staff page automatically; a permission no
 * longer in code is marked inactive, never deleted, so no grant is lost.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionCatalogSync {

    private final PermissionDefinitionRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sync() {
        Map<String, PermissionDefinition> existing = repository.findAll().stream()
                .collect(Collectors.toMap(PermissionDefinition::getKey, Function.identity()));

        int added = 0;
        for (Perm perm : Perm.values()) {
            PermissionDefinition row = existing.remove(perm.key());
            if (row == null) {
                row = new PermissionDefinition();
                row.setKey(perm.key());
                added++;
            }
            row.setArea(perm.area());
            row.setLabel(perm.label());
            row.setDescription(perm.description());
            row.setSortOrder(perm.ordinal());
            row.setActive(true);
            repository.save(row);
        }
        for (PermissionDefinition retired : existing.values()) {
            if (retired.isActive()) {
                retired.setActive(false);
                repository.save(retired);
                log.warn("Permission '{}' is no longer in code; marked inactive, grants kept", retired.getKey());
            }
        }
        log.info("Permission catalogue in sync: {} permissions ({} new)", Perm.values().length, added);
    }
}
