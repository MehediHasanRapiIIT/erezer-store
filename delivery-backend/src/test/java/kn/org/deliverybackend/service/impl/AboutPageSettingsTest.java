package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.settings.AboutPageDTO;
import kn.org.deliverybackend.dto.settings.AboutSectionDTO;
import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The About page as saved from Settings: tidied, and refused when the shop should not show it. */
class AboutPageSettingsTest {

    private final StoreSettingsRepository repo = mock(StoreSettingsRepository.class);
    private final StoreSettingsServiceImpl service = new StoreSettingsServiceImpl(repo, new ObjectMapper().findAndRegisterModules());
    private final StoreSettings row = new StoreSettings();

    @BeforeEach
    void setUp() {
        row.setId(StoreSettings.SINGLETON_ID);
        when(repo.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(row));
        when(repo.save(any(StoreSettings.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StoreSettingsDTO withAbout(AboutPageDTO about) {
        return StoreSettingsDTO.builder().aboutPage(about).build();
    }

    private static AboutSectionDTO section(String heading, String body, String image) {
        return AboutSectionDTO.builder().heading(heading).body(body).imageUrl(image).build();
    }

    @Test
    void aFreshShopGetsDefaultContent() {
        AboutPageDTO about = service.get().getAboutPage();
        assertNotNull(about);
        assertEquals("About Erezer", about.getTitle());
        assertFalse(about.getSections().isEmpty());
        assertEquals("/shop", about.getCtaLink());
    }

    @Test
    void savedTextIsTrimmedAndEmptySectionsDropped() {
        AboutPageDTO saved = service.update(withAbout(AboutPageDTO.builder()
                .title("  Our shop  ")
                .sections(List.of(section("  Story ", "We make clothes.", "  "), section(" ", "", null)))
                .ctaLabel("Shop").ctaLink("/shop")
                .build())).getAboutPage();

        assertEquals("Our shop", saved.getTitle());
        assertEquals(1, saved.getSections().size());
        assertEquals("Story", saved.getSections().get(0).getHeading());
        assertNull(saved.getSections().get(0).getImageUrl());
    }

    @Test
    void aJavascriptLinkIsRefused() {
        InvalidRequestException ex = assertThrows(InvalidRequestException.class, () -> service.update(withAbout(
                AboutPageDTO.builder().title("About").ctaLabel("Click").ctaLink("javascript:alert(1)").build())));
        assertTrue(ex.getMessage().contains("button link"), ex.getMessage());
    }

    @Test
    void aPhotoMustBeAWebAddress() {
        assertThrows(InvalidRequestException.class, () -> service.update(withAbout(
                AboutPageDTO.builder().title("About").heroImageUrl("/local/file.png").build())));
        assertThrows(InvalidRequestException.class, () -> service.update(withAbout(AboutPageDTO.builder()
                .sections(List.of(section("A", "B", "data:image/png;base64,AAAA"))).build())));
    }

    @Test
    void tooManySectionsOrTooMuchTextIsRefused() {
        List<AboutSectionDTO> many = new ArrayList<>(Collections.nCopies(13, section("A", "B", null)));
        assertThrows(InvalidRequestException.class,
                () -> service.update(withAbout(AboutPageDTO.builder().sections(many).build())));
        assertThrows(InvalidRequestException.class,
                () -> service.update(withAbout(AboutPageDTO.builder().title("x".repeat(151)).build())));
    }

    @Test
    void anOlderAdminPanelThatSendsNoAboutPageLeavesItAlone() {
        service.update(withAbout(AboutPageDTO.builder().title("Kept").build()));
        service.update(StoreSettingsDTO.builder().build());
        assertEquals("Kept", service.get().getAboutPage().getTitle());
    }
}
