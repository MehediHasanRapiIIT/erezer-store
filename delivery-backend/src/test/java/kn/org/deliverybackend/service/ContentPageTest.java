package kn.org.deliverybackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.settings.ContentPageDTO;
import kn.org.deliverybackend.entity.ContentPage;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ContentPageRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.impl.ContentPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shop's own pages: written in the admin panel, read by customers while
 * switched on, and linked from the footer for as long as they should be.
 */
class ContentPageTest {

    private final ContentPageRepository repository = mock(ContentPageRepository.class);
    private final StoreSettingsRepository settingsRepository = mock(StoreSettingsRepository.class);
    private final ContentPageService service = new ContentPageService(repository, settingsRepository);
    private final Map<Long, ContentPage> stored = new HashMap<>();
    private final StoreSettings settings = new StoreSettings();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        settings.setFooterJson("{\"brandName\":\"EREZER\",\"columns\":[{\"title\":\"Company\",\"links\":[{\"label\":\"About\",\"url\":\"/about\"}]},"
                + "{\"title\":\"Support\",\"links\":[{\"label\":\"Help\",\"url\":\"/contact\"}]}]}");
        when(settingsRepository.findById(any())).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any(StoreSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(ContentPage.class))).thenAnswer(inv -> {
            ContentPage p = inv.getArgument(0);
            if (p.getId() == null) p.setId(nextId++);
            if (p.getDeleted() == null) p.setDeleted(false);
            stored.put(p.getId(), p);
            return p;
        });
        when(repository.findLive()).thenAnswer(inv -> live());
        when(repository.findLive(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<Long>getArgument(0)))
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted())));
        when(repository.findLiveBySlug(anyString())).thenAnswer(inv -> live().stream()
                .filter(p -> p.getSlug().equalsIgnoreCase(inv.getArgument(0))).findFirst());
    }

    @Test
    void aPageIsSavedWithItsWordsAndReadBackByItsAddress() {
        ContentPageDTO made = service.create(page("Our Mission", "Our mission is simple.\n\nEREZER was built for people who don't want ordinary.",
                section("Creating Beyond the Ordinary", "We challenge conventional ideas."), section("Making Quality Accessible", "Premium should not mean unreachable.")));

        assertEquals("our-mission", made.getSlug());
        ContentPageDTO read = service.getActive("our-mission");
        assertEquals("Our Mission", read.getTitle());
        assertTrue(read.getIntro().contains("\n\n"), "paragraphs are kept");
        assertEquals(List.of("Creating Beyond the Ordinary", "Making Quality Accessible"),
                read.getSections().stream().map(ContentPageDTO.Section::getHeading).toList());
        assertEquals("We challenge conventional ideas.", read.getSections().get(0).getBody());
        assertTrue(read.getIsActive());
    }

    @Test
    void theAddressIsMadeFromTheTitleAndNeverRepeats() {
        assertEquals("our-values", service.create(page("Our Values!", null)).getSlug());
        assertEquals("our-values-2", service.create(page("Our Values", null)).getSlug());
        ContentPageDTO own = page("Anything", null);
        own.setSlug("Size Guide & Care");
        assertEquals("size-guide-care", service.create(own).getSlug());
        assertEquals("page", service.create(page("★★★", null)).getSlug());
    }

    @Test
    void emptySectionsAreLeftOutAndAPageNeedsATitle() {
        ContentPageDTO made = service.create(page("Notes", null, section("Kept", "Text"), section("  ", "  "), section(null, null)));
        assertEquals(1, made.getSections().size());
        assertThrows(InvalidRequestException.class, () -> service.create(page("   ", null)));
    }

    @Test
    void aButtonNeedsBothItsWordsAndItsLink() {
        ContentPageDTO half = page("Shop", null);
        half.setCtaLabel("Shop now");
        assertThrows(InvalidRequestException.class, () -> service.create(half));
        half.setCtaLink("/shop");
        assertEquals("/shop", service.create(half).getCtaLink());
    }

    @Test
    void aPageSwitchedOffCannotBeOpenedButStaysInTheAdminList() {
        ContentPageDTO made = service.create(page("Our Mission", null));
        made.setIsActive(false);
        service.update(made.getId(), made);

        assertThrows(ResourceNotFoundException.class, () -> service.getActive("our-mission"));
        assertTrue(service.listActive().isEmpty());
        assertEquals(1, service.listAll().size());
    }

    // ── the footer ────────────────────────────────────────────────────────────

    @Test
    void aNewPageIsLinkedFromTheFootersFirstColumn() throws Exception {
        service.create(page("Our Mission", null));
        assertEquals(List.of("About > /about", "Our Mission > /pages/our-mission"), links(0));
        assertEquals(List.of("Help > /contact"), links(1), "the other column is left alone");
    }

    @Test
    void theLinkFollowsThePagesTitleAndAddress() throws Exception {
        ContentPageDTO made = service.create(page("Our Mission", null));
        made.setTitle("What We Stand For");
        made.setSlug("what-we-stand-for");
        service.update(made.getId(), made);

        assertEquals(List.of("About > /about", "What We Stand For > /pages/what-we-stand-for"), links(0));
    }

    @Test
    void aLinkTheShopMovedToAnotherColumnStaysThere() throws Exception {
        ContentPageDTO made = service.create(page("Our Mission", null));
        settings.setFooterJson("{\"columns\":[{\"title\":\"Company\",\"links\":[{\"label\":\"About\",\"url\":\"/about\"}]},"
                + "{\"title\":\"Support\",\"links\":[{\"label\":\"Mission\",\"url\":\"/pages/our-mission\"}]}]}");
        made.setIntro("Changed.");
        service.update(made.getId(), made);

        assertEquals(List.of("About > /about"), links(0));
        assertEquals(List.of("Our Mission > /pages/our-mission"), links(1));
    }

    @Test
    void hidingDeletingOrUntickingTheFooterTakesTheLinkAway() throws Exception {
        ContentPageDTO a = service.create(page("Our Mission", null));
        ContentPageDTO b = service.create(page("Our Values", null));
        ContentPageDTO c = service.create(page("Our Story", null));
        assertEquals(4, links(0).size());

        a.setShowInFooter(false);
        service.update(a.getId(), a);
        b.setIsActive(false);
        service.update(b.getId(), b);
        service.delete(c.getId());

        assertEquals(List.of("About > /about"), links(0));
        assertFalse(service.listAll().stream().anyMatch(p -> p.getTitle().equals("Our Story")));

        a.setShowInFooter(true);
        service.update(a.getId(), a);
        assertEquals(List.of("About > /about", "Our Mission > /pages/our-mission"), links(0), "and ticking it again brings it back");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<ContentPage> live() {
        return stored.values().stream().filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .sorted(Comparator.comparing(ContentPage::getSortOrder).thenComparing(ContentPage::getId)).toList();
    }

    private List<String> links(int column) throws Exception {
        JsonNode footer = new ObjectMapper().readTree(settings.getFooterJson());
        List<String> found = new ArrayList<>();
        for (JsonNode link : footer.path("columns").path(column).path("links")) {
            found.add(link.path("label").asText() + " > " + link.path("url").asText());
        }
        return found;
    }

    private static ContentPageDTO page(String title, String intro, ContentPageDTO.Section... sections) {
        return ContentPageDTO.builder().title(title).intro(intro).sections(new ArrayList<>(List.of(sections))).build();
    }

    private static ContentPageDTO.Section section(String heading, String body) {
        return new ContentPageDTO.Section(heading, body, null);
    }
}
