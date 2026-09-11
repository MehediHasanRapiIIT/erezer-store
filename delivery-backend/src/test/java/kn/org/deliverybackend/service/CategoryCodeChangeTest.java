package kn.org.deliverybackend.service;

import kn.org.deliverybackend.service.CategoryCodeChange.CodeMode;
import org.junit.jupiter.api.Test;

import static kn.org.deliverybackend.service.CategoryCodeChange.code;
import static kn.org.deliverybackend.service.CategoryCodeChange.invalidReason;
import static kn.org.deliverybackend.service.CategoryCodeChange.numberOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The rules for giving a category its codes: the same one for all, or a prefix with numbers. */
class CategoryCodeChangeTest {

    @Test
    void theSameCodeOnlyHasToFit() {
        assertNull(invalidReason(CodeMode.SAME, "EP-1001"));
        assertNull(invalidReason(CodeMode.SAME, "  EP 1001 / A  "), "anything the product form accepts");
        assertNotNull(invalidReason(CodeMode.SAME, null), "nothing typed");
        assertNotNull(invalidReason(CodeMode.SAME, "   "), "only spaces");
        assertNotNull(invalidReason(CodeMode.SAME, "X".repeat(41)), "over 40 characters");
        assertNull(invalidReason(CodeMode.SAME, "X".repeat(40)));
    }

    @Test
    void aPrefixIsStricterThanACode() {
        assertNull(invalidReason(CodeMode.NUMBERED, "EP"));
        assertNull(invalidReason(CodeMode.NUMBERED, " ep-2 "));
        assertNull(invalidReason(CodeMode.NUMBERED, "EZ_HD1"));
        assertNotNull(invalidReason(CodeMode.NUMBERED, null), "nothing typed");
        assertNotNull(invalidReason(CodeMode.NUMBERED, "X".repeat(21)), "too long");
        assertNotNull(invalidReason(CodeMode.NUMBERED, "-EP"), "must start with a letter or number");
        assertNotNull(invalidReason(CodeMode.NUMBERED, "EP 1"), "no spaces inside");
        assertNotNull(invalidReason(CodeMode.NUMBERED, "EP/1"), "no slashes");
    }

    @Test
    void aModeMustBeChosen() {
        assertNotNull(invalidReason(null, "EP-1001"));
    }

    @Test
    void numbersAreThreeDigitsAndGrowBeyond999() {
        assertEquals("EP-001", code("EP", 1));
        assertEquals("EP-012", code("EP", 12));
        assertEquals("EP-999", code("EP", 999));
        assertEquals("EP-1000", code("EP", 1000));
        assertEquals("EP-001", code("  EP  ", 1), "spaces around the prefix are dropped");
    }

    @Test
    void aCodesNumberIsReadBack() {
        assertEquals(4, numberOf("EP", "EP-004"));
        assertEquals(4, numberOf("EP", "ep-4"), "however it was typed");
        assertEquals(1200, numberOf("EP", "EP-1200"));
        assertEquals(0, numberOf("EP", "TS-004"), "another prefix doesn't count");
        assertEquals(0, numberOf("EP", "EP-004-A"));
        assertEquals(0, numberOf("EP", "PI-00036"));
        assertEquals(0, numberOf("EP", null));
    }
}
