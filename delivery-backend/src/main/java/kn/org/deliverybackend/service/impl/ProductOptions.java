package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.variant.ProductOptionDTO;
import kn.org.deliverybackend.dto.variant.VariantOptionDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A product's options ("Colour: Black, White") and the combination of choices
 * each variant is.
 *
 * <p>A combination is kept on the variant as a key: "optionId=valueId" pairs
 * joined by "|", in option-id order so the same choices always give the same
 * key whatever order the options are shown in. Ids, not names, so renaming a
 * choice changes nothing but what people read.
 *
 * <p>Everything here is plain arithmetic on those, with no database, so it can
 * be reasoned about and tested on its own.
 */
public final class ProductOptions {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProductOptions() {}

    // ── reading and writing a product's options ──────────────────────────────

    /** The options as stored on a product; empty for none (and for anything unreadable). */
    public static List<ProductOptionDTO> parse(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<ProductOptionDTO> options = JSON.readValue(json, new TypeReference<List<ProductOptionDTO>>() {});
            options.removeIf(o -> o == null || o.getId() == null);
            for (ProductOptionDTO o : options) {
                if (o.getValues() == null) o.setValues(new ArrayList<>());
                o.getValues().removeIf(v -> v == null || v.getId() == null);
            }
            return options;
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /** Null for no options, so a product without any keeps an empty column. */
    public static String write(List<ProductOptionDTO> options) {
        if (options == null || options.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Those options could not be saved.");
        }
    }

    /** A short id for a new option or choice. Never contains the characters a key is built with. */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ── a variant's combination ──────────────────────────────────────────────

    /** The key of these choices (option id → value id), or null for none. */
    public static String key(Map<String, String> choices) {
        if (choices == null || choices.isEmpty()) return null;
        StringBuilder key = new StringBuilder();
        for (Map.Entry<String, String> choice : new TreeMap<>(choices).entrySet()) {
            if (choice.getKey() == null || choice.getValue() == null) continue;
            if (key.length() > 0) key.append('|');
            key.append(choice.getKey()).append('=').append(choice.getValue());
        }
        return key.length() == 0 ? null : key.toString();
    }

    /** The choices a key stands for (option id → value id); empty for null. */
    public static Map<String, String> choices(String key) {
        Map<String, String> choices = new LinkedHashMap<>();
        if (key == null || key.isBlank()) return choices;
        for (String pair : key.split("\\|")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && eq < pair.length() - 1) choices.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return choices;
    }

    /**
     * Every combination these options allow, as keys, in the order a person
     * would list them: the first option's choices outermost. One null key for
     * no options, so "a product with no options" is a single combination like
     * any other.
     */
    public static List<String> combinations(List<ProductOptionDTO> options) {
        List<Map<String, String>> all = new ArrayList<>();
        all.add(new LinkedHashMap<>());
        for (ProductOptionDTO option : options == null ? List.<ProductOptionDTO>of() : options) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> soFar : all) {
                for (ProductOptionDTO.Value value : option.getValues()) {
                    Map<String, String> with = new LinkedHashMap<>(soFar);
                    with.put(option.getId(), value.getId());
                    next.add(with);
                }
            }
            all = next;
        }
        List<String> keys = new ArrayList<>(all.size());
        for (Map<String, String> choices : all) keys.add(key(choices));
        return keys;
    }

    /** How many combinations, without building them. */
    public static long count(List<ProductOptionDTO> options) {
        long count = 1;
        for (ProductOptionDTO option : options == null ? List.<ProductOptionDTO>of() : options) {
            count *= Math.max(option.getValues().size(), 1);
            if (count > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return count;
    }

    /**
     * A key's choices spelt out, in the order the options are shown. A choice
     * whose option or value no longer exists is left out.
     */
    public static List<VariantOptionDTO> describe(List<ProductOptionDTO> options, String key) {
        Map<String, String> choices = choices(key);
        List<VariantOptionDTO> described = new ArrayList<>();
        for (ProductOptionDTO option : options == null ? List.<ProductOptionDTO>of() : options) {
            String valueId = choices.get(option.getId());
            if (valueId == null) continue;
            for (ProductOptionDTO.Value value : option.getValues()) {
                if (valueId.equals(value.getId())) {
                    described.add(new VariantOptionDTO(option.getId(), option.getName(), value.getId(), value.getValue(), value.getHex()));
                }
            }
        }
        return described;
    }

    /** "Black / Long Sleeve" for a key; null for no choices. */
    public static String label(List<ProductOptionDTO> options, String key) {
        List<String> words = describe(options, key).stream().map(VariantOptionDTO::getValue).toList();
        return words.isEmpty() ? null : String.join(" / ", words);
    }

    /** What a variant is called: "Black / Long / Drop Shoulder / M", from whichever of those it has. */
    public static String variantName(List<ProductOptionDTO> options, String key, String fit, String size) {
        String combination = label(options, key);
        String fitAndSize = kn.org.deliverybackend.enumeration.Fit.describe(fit, size);
        if (combination == null) return fitAndSize;
        String name = fitAndSize == null || fitAndSize.isBlank() ? combination : combination + " / " + fitAndSize;
        // The columns that keep a name hold 255 letters; many long options could pass that.
        return name.length() > 250 ? name.substring(0, 249) + "…" : name;
    }

    /**
     * True when a key is one of the combinations these options allow: a choice
     * for every option, each one that exists, and nothing else.
     */
    public static boolean allows(List<ProductOptionDTO> options, String key) {
        Map<String, String> choices = choices(key);
        List<ProductOptionDTO> all = options == null ? List.of() : options;
        if (choices.size() != all.size()) return false;
        for (ProductOptionDTO option : all) {
            String valueId = choices.get(option.getId());
            if (valueId == null || option.getValues().stream().noneMatch(v -> valueId.equals(v.getId()))) return false;
        }
        return true;
    }
}
