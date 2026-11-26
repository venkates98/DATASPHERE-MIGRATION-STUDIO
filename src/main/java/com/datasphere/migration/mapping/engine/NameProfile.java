package com.datasphere.migration.mapping.engine;

import java.util.List;

/**
 * A column name prepared for comparison.
 *
 * @param key   upper-case name without separators ({@code EMPLOYEEID})
 * @param terms the words of the name after abbreviation expansion, without filler words such as OF/THE
 */
public record NameProfile(String name, String key, List<Term> terms) {

    /**
     * One word of a name.
     *
     * @param original the word as written ({@code EMP})
     * @param word     the word after expansion ({@code EMPLOYEE})
     * @param expanded whether {@code word} came from the abbreviation dictionary
     */
    public record Term(String original, String word, boolean expanded) {

        public String stem() {
            return NameNormalizer.stem(word);
        }
    }

    /** The words joined without separators, used for character-level similarity. */
    public String expandedKey() {
        StringBuilder sb = new StringBuilder();
        terms.forEach(t -> sb.append(t.word()));
        return sb.toString();
    }
}
