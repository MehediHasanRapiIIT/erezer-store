package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.entity.SizeFinderSetting;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.SizeFinderSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The size finder's rules ("Don't know your size? Click here" on a product
 * page), set on the Size Charts page.
 *
 * <p>Each fit has its own table of sizes, each with the tallest height and the
 * heaviest weight it suits; products with no fit use the General table. In the
 * shop the customer's height gives one size, their weight another, and the
 * larger of the two is recommended. The finder can be switched off for one fit,
 * for products with no fit, or for the whole shop. Until the admin changes
 * anything, every table holds the standard rules the shop has always used.
 */
@Service
@RequiredArgsConstructor
public class SizeFinderService {

    public static final String GENERAL = "_GENERAL";
    public static final String SHOP = "_SHOP";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SizeFinderSettingRepository settings;

    /** One size of a table: the tallest height (in inches) and heaviest weight (kg) it suits; null for "anything above". */
    public record Rule(String size, Integer heightUpToIn, Integer weightUpToKg) {
    }

    /** One table: on or off, and its sizes from smallest to largest. */
    public record Table(boolean enabled, List<Rule> rules) {
    }

    /** Everything the shop needs: the shop-wide switch, the General table, and one table per fit. */
    public record Finder(boolean enabled, Table general, Map<String, Table> fits) {
    }

    /** The rules the shop has always used: under 165 cm → S … and under 55 kg → S …. */
    public static List<Rule> standardRules() {
        return List.of(
                new Rule("S", 64, 54),
                new Rule("M", 67, 64),
                new Rule("L", 70, 77),
                new Rule("XL", 72, 89),
                new Rule("XXL", 75, 104),
                new Rule("3XL", null, null));
    }

    @Transactional(readOnly = true)
    public Finder finder() {
        Map<String, SizeFinderSetting> stored = new LinkedHashMap<>();
        for (SizeFinderSetting s : settings.findAll()) stored.put(s.getScope(), s);
        SizeFinderSetting shop = stored.get(SHOP);
        Map<String, Table> fits = new LinkedHashMap<>();
        for (Fit fit : Fit.values()) fits.put(fit.name(), table(stored.get(fit.name())));
        return new Finder(shop == null || !Boolean.FALSE.equals(shop.getEnabled()), table(stored.get(GENERAL)), fits);
    }

    /** Switches the finder on or off for the whole shop. */
    @Transactional
    public Finder setShopEnabled(boolean enabled) {
        SizeFinderSetting shop = settings.findById(SHOP).orElse(new SizeFinderSetting(SHOP, true, null));
        shop.setEnabled(enabled);
        settings.save(shop);
        return finder();
    }

    /**
     * Saves one table: {@link #GENERAL} or a fit's code. Null rules keep the
     * table's sizes and only switch it; an empty list puts the standard rules back.
     */
    @Transactional
    public Finder save(String scope, Boolean enabled, List<Rule> rules) {
        String key = checkedScope(scope);
        SizeFinderSetting row = settings.findById(key).orElse(new SizeFinderSetting(key, true, null));
        if (enabled != null) row.setEnabled(enabled);
        if (rules != null) row.setRulesJson(rules.isEmpty() ? null : write(checked(rules)));
        settings.save(row);
        return finder();
    }

    /** A fit that is removed takes its table with it. */
    @Transactional
    public void forgetFit(String fitCode) {
        if (fitCode != null) settings.deleteById(fitCode);
    }

    // ── checks ────────────────────────────────────────────────────────────────

    private static String checkedScope(String scope) {
        if (scope == null || scope.isBlank()) throw new InvalidRequestException("Which table?");
        String key = scope.trim().toUpperCase(Locale.ROOT);
        if (GENERAL.equals(key) || "GENERAL".equals(key)) return GENERAL;
        Fit fit = Fit.parse(scope);
        if (fit == null) throw new InvalidRequestException("Which fit?");
        return fit.name();
    }

    /** The table as it may be saved: sizes named once, and every "up to" larger than the size before it. */
    static List<Rule> checked(List<Rule> rules) {
        if (rules.size() > 15) throw new InvalidRequestException("A table can have at most 15 sizes.");
        List<Rule> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Integer lastHeight = null, lastWeight = null;
        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            String size = r == null || r.size() == null ? "" : r.size().trim();
            if (size.isEmpty()) throw new InvalidRequestException("Every row needs a size, such as M.");
            if (size.length() > 10) throw new InvalidRequestException("A size can be at most 10 letters: " + size + ".");
            if (!seen.add(size.toUpperCase(Locale.ROOT))) throw new InvalidRequestException("Size " + size + " is in the table twice.");
            boolean last = i == rules.size() - 1;
            Integer h = r.heightUpToIn(), w = r.weightUpToKg();
            if (!last && (h == null || w == null)) {
                throw new InvalidRequestException("Size " + size + " needs both a height and a weight. Only the largest size may leave them empty (“anything above”).");
            }
            if (h != null && (h < 36 || h > 96)) throw new InvalidRequestException("Size " + size + ": the height has to be between 3 ft and 8 ft.");
            if (w != null && (w < 10 || w > 250)) throw new InvalidRequestException("Size " + size + ": the weight has to be between 10 and 250 kg.");
            if (h != null && lastHeight != null && h <= lastHeight) {
                throw new InvalidRequestException("Size " + size + ": its height has to be more than the size before it.");
            }
            if (w != null && lastWeight != null && w <= lastWeight) {
                throw new InvalidRequestException("Size " + size + ": its weight has to be more than the size before it.");
            }
            if (h != null) lastHeight = h;
            if (w != null) lastWeight = w;
            out.add(new Rule(size, h, w));
        }
        if (out.isEmpty()) throw new InvalidRequestException("A table needs at least one size.");
        return out;
    }

    private static Table table(SizeFinderSetting row) {
        if (row == null) return new Table(true, standardRules());
        List<Rule> rules = read(row.getRulesJson());
        return new Table(!Boolean.FALSE.equals(row.getEnabled()), rules.isEmpty() ? standardRules() : rules);
    }

    private static List<Rule> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<Rule>>() { });
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String write(List<Rule> rules) {
        try {
            return JSON.writeValueAsString(rules);
        } catch (IOException e) {
            throw new InvalidRequestException("The table could not be saved.");
        }
    }
}
