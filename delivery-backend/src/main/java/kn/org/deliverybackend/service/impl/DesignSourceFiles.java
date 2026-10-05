package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.customdesign.CustomOrderSourceFileDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the original pictures a custom design was made from.
 *
 * <p>A design is saved as the studio's own description of each side of the
 * garment: {@code {"front": {"objects": [...]}, "back": {...}}}. Every picture
 * on it records the address of the file it was loaded from — the customer's
 * upload, stored untouched, or a logo from the shop's library — and that
 * picture's real size in pixels. This reads them back out, so staff can print
 * from the original file rather than from the screen-size preview.
 */
final class DesignSourceFiles {

    private static final ObjectMapper JSON = new ObjectMapper();

    private DesignSourceFiles() {}

    /**
     * @param designJson the saved design; anything unreadable gives an empty list
     * @param shopLogos  the shop's library, address → name, to tell its logos from a customer's files
     */
    static List<CustomOrderSourceFileDTO> from(String designJson, Map<String, String> shopLogos) {
        List<CustomOrderSourceFileDTO> files = new ArrayList<>();
        if (designJson == null || designJson.isBlank()) return files;

        JsonNode design;
        try {
            design = JSON.readTree(designJson);
        } catch (Exception unreadable) {
            return files;
        }
        if (!design.isObject()) return files;

        Iterator<Map.Entry<String, JsonNode>> views = design.fields();
        while (views.hasNext()) {
            Map.Entry<String, JsonNode> view = views.next();
            // The same file placed twice on one side is one file to print from.
            Set<String> seen = new HashSet<>();
            collect(view.getValue().path("objects"), view.getKey(), shopLogos, seen, files);
        }
        return files;
    }

    private static void collect(JsonNode objects, String view, Map<String, String> shopLogos,
                                Set<String> seen, List<CustomOrderSourceFileDTO> out) {
        if (!objects.isArray()) return;
        for (JsonNode object : objects) {
            // Things grouped together keep their own pictures inside the group.
            collect(object.path("objects"), view, shopLogos, seen, out);

            if (!"image".equalsIgnoreCase(object.path("type").asText())) continue;
            String src = object.path("src").asText("");
            if (src.isBlank() || !seen.add(src)) continue;

            String kind;
            String name = null;
            if (src.startsWith("data:")) {
                kind = CustomOrderSourceFileDTO.EDITED;
            } else if (shopLogos.containsKey(src)) {
                kind = CustomOrderSourceFileDTO.SHOP;
                name = shopLogos.get(src);
            } else {
                kind = CustomOrderSourceFileDTO.CUSTOMER;
            }
            out.add(CustomOrderSourceFileDTO.builder()
                    .view(view)
                    .url(src)
                    .kind(kind)
                    .name(name)
                    .width(pixels(object.path("width")))
                    .height(pixels(object.path("height")))
                    .build());
        }
    }

    /** A picture's own width or height: a whole, positive number of pixels, or null. */
    private static Integer pixels(JsonNode value) {
        if (!value.isNumber()) return null;
        long rounded = Math.round(value.asDouble());
        return rounded > 0 && rounded < 100_000 ? (int) rounded : null;
    }
}
