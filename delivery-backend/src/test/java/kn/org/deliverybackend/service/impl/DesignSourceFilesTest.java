package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.customdesign.CustomOrderSourceFileDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Staff print a custom order from the original files, not from the screen-size
 * preview: the customer's uploads and the shop's logos are read back out of the
 * saved design, at their own size.
 */
class DesignSourceFilesTest {

    private static final String UPLOAD = "http://files/custom-design-images/aaa.png";
    private static final String LOGO = "http://files/custom-design-images/logo.png";
    private static final Map<String, String> SHOP = Map.of(LOGO, "Erezer wordmark");

    private static String image(String src, int width, int height) {
        return "{\"type\":\"Image\",\"src\":\"" + src + "\",\"width\":" + width + ",\"height\":" + height
                + ",\"scaleX\":0.1,\"scaleY\":0.1}";
    }

    private static final String TEXT = "{\"type\":\"IText\",\"text\":\"Hello\",\"width\":170.9,\"height\":113}";

    @Test
    void aCustomersUploadIsListedAtItsOwnSize() {
        // Shown at a tenth of its size on the canvas; the file itself is still 3000 x 2000.
        String design = "{\"front\":{\"objects\":[" + TEXT + "," + image(UPLOAD, 3000, 2000) + "]}}";

        List<CustomOrderSourceFileDTO> files = DesignSourceFiles.from(design, SHOP);

        assertEquals(1, files.size(), "words are not files");
        CustomOrderSourceFileDTO file = files.get(0);
        assertEquals("front", file.getView());
        assertEquals(UPLOAD, file.getUrl());
        assertEquals(CustomOrderSourceFileDTO.CUSTOMER, file.getKind());
        assertEquals(3000, file.getWidth());
        assertEquals(2000, file.getHeight());
        assertNull(file.getName());
    }

    @Test
    void aShopLogoIsToldApartAndNamed() {
        String design = "{\"back\":{\"objects\":[" + image(LOGO, 1200, 400) + "]}}";
        CustomOrderSourceFileDTO file = DesignSourceFiles.from(design, SHOP).get(0);
        assertEquals(CustomOrderSourceFileDTO.SHOP, file.getKind());
        assertEquals("Erezer wordmark", file.getName());
    }

    @Test
    void everySideIsRead() {
        String design = "{\"front\":{\"objects\":[" + image(UPLOAD, 3000, 2000) + "]},"
                + "\"back\":{\"objects\":[" + image(LOGO, 1200, 400) + "]},"
                + "\"leftSleeve\":{\"objects\":[]}}";
        List<CustomOrderSourceFileDTO> files = DesignSourceFiles.from(design, SHOP);
        assertEquals(List.of("front", "back"), files.stream().map(CustomOrderSourceFileDTO::getView).toList());
    }

    @Test
    void theSameFileTwiceOnOneSideIsListedOnce() {
        String design = "{\"front\":{\"objects\":[" + image(UPLOAD, 3000, 2000) + "," + image(UPLOAD, 3000, 2000) + "]},"
                + "\"back\":{\"objects\":[" + image(UPLOAD, 3000, 2000) + "]}}";
        assertEquals(2, DesignSourceFiles.from(design, SHOP).size(), "once for the front, once for the back");
    }

    @Test
    void picturesInsideAGroupAreFound() {
        String design = "{\"front\":{\"objects\":[{\"type\":\"Group\",\"objects\":[" + image(UPLOAD, 800, 800) + "]}]}}";
        assertEquals(1, DesignSourceFiles.from(design, SHOP).size());
    }

    @Test
    void aPictureChangedInTheStudioIsMarkedEdited() {
        // Removing a background replaces the picture with the studio's own full-size copy.
        String design = "{\"front\":{\"objects\":[" + image("data:image/png;base64,iVBORw0KGgo=", 2400, 2400) + "]}}";
        CustomOrderSourceFileDTO file = DesignSourceFiles.from(design, SHOP).get(0);
        assertEquals(CustomOrderSourceFileDTO.EDITED, file.getKind());
        assertTrue(file.getUrl().startsWith("data:image/png"));
        assertEquals(2400, file.getWidth());
    }

    @Test
    void aDesignThatCannotBeReadGivesNoFilesRatherThanAnError() {
        assertTrue(DesignSourceFiles.from(null, SHOP).isEmpty());
        assertTrue(DesignSourceFiles.from("", SHOP).isEmpty());
        assertTrue(DesignSourceFiles.from("not json", SHOP).isEmpty());
        assertTrue(DesignSourceFiles.from("[1,2,3]", SHOP).isEmpty());
        assertTrue(DesignSourceFiles.from("{\"front\":{\"objects\":\"nope\"}}", SHOP).isEmpty());
    }
}
