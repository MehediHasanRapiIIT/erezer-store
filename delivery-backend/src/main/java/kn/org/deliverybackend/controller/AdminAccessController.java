package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.AccessRule;
import kn.org.deliverybackend.access.AdminOnly;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Every endpoint a staff login can reach, with the rule that guards it, read
 * from the running code. {@code deploy/verify_access_control.py} walks this
 * list, so a new admin endpoint is swept automatically.
 */
@RestController
@RequestMapping("/admin/access")
@Tag(name = "Admin: Me")
public class AdminAccessController {

    /** Catalogue paths whose POST, PUT and DELETE belong to the staff security chain. */
    private static final List<String> CATALOGUE = List.of("/api/products", "/api/categories", "/api/banners");
    private static final Set<RequestMethod> CATALOGUE_CHANGES =
            EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE);
    /** Written by customers with their own login; kept out of the staff chain on purpose. */
    private static final Pattern CUSTOMER_REVIEWS = Pattern.compile("^/api/products/[^/]+/reviews(/.*)?$");

    private final RequestMappingHandlerMapping handlerMapping;

    public AdminAccessController(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /**
     * One entry per method and path. {@code rule} is ADMIN_ONLY, ANY_STAFF,
     * ALL_OF or ANY_OF; for the last two, {@code permissions} lists the keys.
     * {@code consumes} lists the body types the endpoint accepts, if limited.
     */
    public record EndpointRule(String method, String path, String rule, List<String> permissions, List<String> consumes) {}

    @GetMapping("/endpoints")
    @AdminOnly
    public List<EndpointRule> endpoints() {
        List<EndpointRule> result = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            AccessRule rule = AccessRule.of(handler);
            List<String> consumes = info.getConsumesCondition().getConsumableMediaTypes().stream()
                    .map(MediaType::toString).toList();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            Set<RequestMethod> verbs = methods.isEmpty()
                    ? EnumSet.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE)
                    : methods;
            for (String path : info.getPatternValues()) {
                for (RequestMethod verb : verbs) {
                    if (staffChain(verb, path)) {
                        result.add(new EndpointRule(verb.name(), path, name(rule),
                                Arrays.stream(rule.perms()).map(Perm::key).toList(), consumes));
                    }
                }
            }
        });
        result.sort(Comparator.comparing(EndpointRule::path).thenComparing(EndpointRule::method));
        return result;
    }

    private static String name(AccessRule rule) {
        return switch (rule.kind()) {
            case ADMIN_ONLY -> "ADMIN_ONLY";
            case ANY_STAFF -> "ANY_STAFF";
            case PERMISSIONS -> rule.mode() == RequiresPermission.Mode.ANY ? "ANY_OF" : "ALL_OF";
        };
    }

    private static boolean staffChain(RequestMethod verb, String path) {
        if (path.startsWith("/admin")) return true;
        return CATALOGUE_CHANGES.contains(verb)
                && CATALOGUE.stream().anyMatch(path::startsWith)
                && !CUSTOMER_REVIEWS.matcher(path).matches();
    }
}
