package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.HomeSectionDTO;
import kn.org.deliverybackend.enumeration.HomeSection;
import kn.org.deliverybackend.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The rules for the home page's layout: which sections, in what order, and
 * whether each is shown.
 *
 * <p>A layout always names every section exactly once, so the shop and the
 * admin screen never have to guess about one that is missing.
 */
public final class HomeLayout {

    private HomeLayout() {}

    /** The page as it was built: every section, in its original order, switched on. */
    public static List<HomeSectionDTO> original() {
        List<HomeSectionDTO> all = new ArrayList<>();
        for (HomeSection section : HomeSection.values()) {
            all.add(new HomeSectionDTO(section.name(), true));
        }
        return all;
    }

    /**
     * Makes whatever was stored into a complete layout. Sections it names stay
     * in its order; anything it doesn't know is dropped; and a section it
     * doesn't mention — one added to the shop since it was saved — goes at the
     * end, switched on. Nothing stored at all gives {@link #original()}.
     */
    public static List<HomeSectionDTO> complete(List<HomeSectionDTO> stored) {
        List<HomeSectionDTO> layout = new ArrayList<>();
        Set<HomeSection> placed = EnumSet.noneOf(HomeSection.class);
        if (stored != null) {
            for (HomeSectionDTO row : stored) {
                if (row == null) continue;
                HomeSection section = HomeSection.parse(row.getKey()).orElse(null);
                if (section != null && placed.add(section)) {
                    layout.add(new HomeSectionDTO(section.name(), row.isEnabled()));
                }
            }
        }
        for (HomeSection section : HomeSection.values()) {
            if (placed.add(section)) {
                layout.add(new HomeSectionDTO(section.name(), true));
            }
        }
        return layout;
    }

    /**
     * Checks a layout an admin is saving, and completes it. Stricter than
     * {@link #complete}: a section it doesn't know, or one listed twice, is a
     * mistake to report rather than quietly tidy away.
     */
    public static List<HomeSectionDTO> checked(List<HomeSectionDTO> requested) {
        if (requested == null) {
            throw new InvalidRequestException("Send the home page's sections.");
        }
        Set<HomeSection> seen = EnumSet.noneOf(HomeSection.class);
        for (HomeSectionDTO row : requested) {
            HomeSection section = row == null ? null : HomeSection.parse(row.getKey()).orElse(null);
            if (section == null) {
                throw new InvalidRequestException(
                        "\"" + (row == null ? null : row.getKey()) + "\" is not a section of the home page.");
            }
            if (!seen.add(section)) {
                throw new InvalidRequestException("The section " + section.name() + " is listed twice.");
            }
        }
        return complete(requested);
    }
}
