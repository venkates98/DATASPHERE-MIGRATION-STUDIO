package com.datasphere.migration.mapping.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurable vocabulary of abbreviations and synonyms.
 *
 * <p>File format ({@code java.util.Properties}):
 * <pre>
 * abbr.EMP=EMPLOYEE              # one abbreviation, one expansion (words joined with _)
 * abbr.DOB=DATE_OF_BIRTH
 * context.suffix=ID,CD,NO        # expanded only as the last word of a multi-word name (EMP_ID, not ID)
 * synonym.strong.1=PHONE,TELEPHONE   # interchangeable words
 * synonym.weak.1=IDENTIFIER,NUMBER   # loosely related words; only partial credit and always reviewed
 * </pre>
 * The built-in file is {@code mapping/abbreviations.properties}; an optional external file extends or overrides it.
 */
public final class AbbreviationDictionary {

    private static final Logger log = LoggerFactory.getLogger(AbbreviationDictionary.class);
    private static final String DEFAULT_RESOURCE = "/mapping/abbreviations.properties";
    private static final Pattern ABBREVIATION = Pattern.compile("[A-Z0-9]{1,20}");
    private static final Pattern EXPANSION = Pattern.compile("[A-Z0-9]+(_[A-Z0-9]+)*");

    private final Map<String, List<String>> expansions = new HashMap<>();
    private final Set<String> contextSensitive = new HashSet<>();
    private final Map<String, Integer> strongGroups = new HashMap<>();
    private final Map<String, Integer> weakGroups = new HashMap<>();

    private AbbreviationDictionary() {
    }

    /** Built-in dictionary, optionally extended by {@code extension} (may be null or blank). */
    public static AbbreviationDictionary load(String extension) {
        Properties merged = new Properties();
        try (InputStream in = AbbreviationDictionary.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing built-in " + DEFAULT_RESOURCE);
            }
            try (Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                merged.load(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read built-in abbreviation dictionary", e);
        }
        if (extension != null && !extension.isBlank()) {
            Path path = Path.of(extension);
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Properties extra = new Properties();
                extra.load(reader);
                // Synonym groups are numbered per file, so extension groups are renumbered to avoid clobbering.
                for (String key : extra.stringPropertyNames()) {
                    String target = key.startsWith("synonym.") ? "ext." + key : key;
                    merged.setProperty(target, extra.getProperty(key));
                }
                log.info("Loaded abbreviation dictionary extension from {}", path);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read abbreviation dictionary file " + path, e);
            }
        }
        return fromProperties(merged);
    }

    public static AbbreviationDictionary fromProperties(Properties p) {
        AbbreviationDictionary d = new AbbreviationDictionary();
        Map<String, String> sorted = new TreeMap<>();
        for (String key : p.stringPropertyNames()) {
            sorted.put(key, p.getProperty(key));
        }
        int strong = 0;
        int weak = 0;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String key = e.getKey();
            String value = e.getValue().trim().toUpperCase(Locale.ROOT);
            if (key.startsWith("abbr.")) {
                String abbreviation = key.substring(5).toUpperCase(Locale.ROOT);
                if (ABBREVIATION.matcher(abbreviation).matches() && EXPANSION.matcher(value).matches()) {
                    d.expansions.put(abbreviation, Arrays.asList(value.split("_")));
                } else {
                    log.warn("Ignoring invalid abbreviation entry '{}'", key);
                }
            } else if (key.equals("context.suffix")) {
                Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(d.contextSensitive::add);
            } else if (key.matches("(ext\\.)?synonym\\.strong\\..+")) {
                d.addGroup(d.strongGroups, value, ++strong);
            } else if (key.matches("(ext\\.)?synonym\\.weak\\..+")) {
                d.addGroup(d.weakGroups, value, ++weak);
            }
        }
        return d;
    }

    private void addGroup(Map<String, Integer> groups, String csv, int id) {
        for (String word : csv.split(",")) {
            String w = word.trim();
            if (!w.isEmpty()) {
                groups.putIfAbsent(NameNormalizer.stem(w), id);
            }
        }
    }

    /**
     * Expands an abbreviation to its words.
     *
     * @param index position of the word in the name; {@code count} how many words the name has
     * @return the expansion, or an empty list when the word is not an abbreviation here. Context-sensitive
     *         abbreviations (ID, NO, CD ...) are only expanded as the last word of a multi-word name, because
     *         a lone {@code NO} or {@code ID} is as likely to mean something else.
     */
    public List<String> expand(String word, int index, int count) {
        List<String> expansion = expansions.get(word);
        if (expansion == null) {
            return List.of();
        }
        if (contextSensitive.contains(word) && !(count >= 2 && index == count - 1)) {
            return List.of();
        }
        return new ArrayList<>(expansion);
    }

    public boolean areStrongSynonyms(String a, String b) {
        return sameGroup(strongGroups, a, b);
    }

    public boolean areWeakSynonyms(String a, String b) {
        return sameGroup(weakGroups, a, b);
    }

    private static boolean sameGroup(Map<String, Integer> groups, String a, String b) {
        Integer ga = groups.get(NameNormalizer.stem(a));
        return ga != null && ga.equals(groups.get(NameNormalizer.stem(b)));
    }

    public int abbreviationCount() {
        return expansions.size();
    }
}
