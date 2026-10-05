package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One picture a custom order was made from, at its original size: the file the
 * customer uploaded, or a logo from the shop's library.
 *
 * <p>The order's preview images are a flattened picture of the garment, drawn
 * at screen size. This is the file itself, untouched since it was uploaded, and
 * is what to print from.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderSourceFileDTO {

    /** Which side of the garment it is on: front, back, … */
    private String view;

    /** Where the original file is. For {@link #EDITED} this is the picture itself, as a data: address. */
    private String url;

    /** {@link #CUSTOMER}, {@link #SHOP} or {@link #EDITED}. */
    private String kind;

    /** The logo's name in the shop's library; null for a customer's file. */
    private String name;

    /** The picture's own size in pixels, or null if the design did not record it. */
    private Integer width;
    private Integer height;

    /** A file the customer uploaded. */
    public static final String CUSTOMER = "CUSTOMER";
    /** A logo from the shop's own library. */
    public static final String SHOP = "SHOP";
    /** A picture the customer changed in the studio (background removed); kept at full size inside the design. */
    public static final String EDITED = "EDITED";
}
