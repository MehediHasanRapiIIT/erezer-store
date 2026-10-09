package kn.org.deliverybackend.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** A page of the shop's own words, as it is read and as it is sent to be saved. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentPageDTO {
    private Long id;
    /** The last part of the address. Left empty when saving, it is made from the title. */
    @Size(max = 140, message = "The web address can be up to 140 letters")
    private String slug;
    @NotBlank(message = "Give the page a title")
    @Size(max = 160, message = "The title can be up to 160 letters")
    private String title;
    @Size(max = 80, message = "The small line above the title can be up to 80 letters")
    private String eyebrow;
    @Size(max = 4000, message = "The opening can be up to 4000 letters")
    private String intro;
    @Size(max = 1000)
    private String heroImageUrl;
    @Builder.Default
    private List<Section> sections = new ArrayList<>();
    @Size(max = 1000, message = "The closing line can be up to 1000 letters")
    private String closing;
    @Size(max = 80, message = "The button's words can be up to 80 letters")
    private String ctaLabel;
    @Size(max = 300)
    private String ctaLink;
    private Boolean showInFooter;
    private Boolean isActive;
    private Integer sortOrder;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String heading;
        private String body;
        private String imageUrl;
    }
}
