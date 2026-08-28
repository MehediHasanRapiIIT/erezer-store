package kn.org.deliverybackend.dto.request.banner;

import kn.org.deliverybackend.enumeration.BannerSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Everything about a banner except its image file.
 *
 * <p>The upload/update endpoints are multipart, so their fields arrive as loose
 * request params. Bundling them here keeps the service signature readable — the
 * alternative was nine positional parameters, most of them nullable strings,
 * which is easy to transpose at a call site and impossible to read at a glance.
 *
 * <p>Every field is optional. On update, a null means "leave as-is" rather than
 * "clear it", matching how the endpoint already behaved for title and dates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerContentDTO {

    private String promotionTitle;
    private String promotionDetails;
    private LocalDate fromDate;
    private LocalDate toDate;

    /** Home-page band. Null is treated as HERO by the storefront. */
    private BannerSlot slot;

    private String ctaLabel;
    private String ctaLink;
    private Integer sortOrder;
}
