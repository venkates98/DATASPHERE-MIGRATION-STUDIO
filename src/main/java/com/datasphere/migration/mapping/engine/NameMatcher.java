package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.mapping.engine.NameProfile.Term;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Compares two column (or table) names with exact, abbreviation, synonym and similarity evidence.
 * Pure and deterministic: the same two names always give the same {@link NameEvidence}.
 */
public final class NameMatcher {

    private static final Set<String> FILLER_WORDS = Set.of("OF", "THE", "A", "AN", "AND");
    private static final double STRONG = 1.0;
    private static final double WEAK = 0.5;

    private final AbbreviationDictionary dictionary;

    public NameMatcher(AbbreviationDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /** Prepares a name once so it can be compared with many others cheaply. */
    public NameProfile profile(String name) {
        List<String> tokens = NameNormalizer.tokens(name);
        List<Term> terms = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            List<String> expansion = dictionary.expand(token, i, tokens.size());
            if (expansion.isEmpty()) {
                terms.add(new Term(token, token, false));
            } else {
                expansion.forEach(word -> terms.add(new Term(token, word, true)));
            }
        }
        List<Term> meaningful = terms.stream().filter(t -> !FILLER_WORDS.contains(t.word())).toList();
        return new NameProfile(name, String.join("", tokens), meaningful.isEmpty() ? terms : meaningful);
    }

    public NameEvidence compare(NameProfile a, NameProfile b) {
        if (!a.key().isEmpty() && a.key().equals(b.key())) {
            return new NameEvidence(EvidenceTier.EXACT, 1.0, 1.0,
                    List.of("The names are the same when case and separators are ignored."));
        }
        if (a.terms().isEmpty() || b.terms().isEmpty()) {
            return NameEvidence.none();
        }

        List<Match> matches = align(a.terms(), b.terms());
        double matched = matches.stream().mapToDouble(Match::weight).sum();
        double coverage = 2.0 * matched / (a.terms().size() + b.terms().size());
        double similarity = Math.max(levenshteinSimilarity(a.expandedKey(), b.expandedKey()), tokenSimilarity(a, b));

        EvidenceTier tier;
        if (matches.isEmpty()) {
            tier = similarity > 0 ? EvidenceTier.SIMILARITY_ONLY : EvidenceTier.NONE;
        } else if (coverage >= 0.999) {
            boolean viaDictionary = matches.stream().anyMatch(m -> m.kind != Kind.LITERAL);
            tier = viaDictionary ? EvidenceTier.ABBREVIATION_OR_SYNONYM : EvidenceTier.REORDERED_WORDS;
        } else {
            tier = EvidenceTier.PARTIAL;
        }
        return new NameEvidence(tier, coverage, similarity,
                reasons(a, b, matches, tier, similarity).stream().distinct().toList());
    }

    // ------------------------------------------------------------------ word alignment

    private enum Kind { LITERAL, ABBREVIATION, SYNONYM, WEAK_SYNONYM }

    private record Match(int source, int target, Kind kind, double weight) {
    }

    /** Greedy one-to-one pairing of words, strongest evidence first, ties broken by position (deterministic). */
    private List<Match> align(List<Term> a, List<Term> b) {
        List<Match> candidates = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            for (int j = 0; j < b.size(); j++) {
                Match m = relate(i, j, a.get(i), b.get(j));
                if (m != null) {
                    candidates.add(m);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Match::weight).reversed()
                .thenComparingInt(Match::source).thenComparingInt(Match::target));
        boolean[] usedA = new boolean[a.size()];
        boolean[] usedB = new boolean[b.size()];
        List<Match> chosen = new ArrayList<>();
        for (Match m : candidates) {
            if (!usedA[m.source] && !usedB[m.target]) {
                usedA[m.source] = true;
                usedB[m.target] = true;
                chosen.add(m);
            }
        }
        return chosen;
    }

    private Match relate(int i, int j, Term x, Term y) {
        if (x.stem().equals(y.stem())) {
            Kind kind = x.expanded() || y.expanded() ? Kind.ABBREVIATION : Kind.LITERAL;
            return new Match(i, j, kind, STRONG);
        }
        if (dictionary.areStrongSynonyms(x.word(), y.word())) {
            return new Match(i, j, Kind.SYNONYM, STRONG);
        }
        if (dictionary.areWeakSynonyms(x.word(), y.word())) {
            return new Match(i, j, Kind.WEAK_SYNONYM, WEAK);
        }
        return null;
    }

    // ------------------------------------------------------------------ reasons

    private List<String> reasons(NameProfile a, NameProfile b, List<Match> matches, EvidenceTier tier,
                                 double similarity) {
        List<String> reasons = new ArrayList<>();
        List<String> shared = new ArrayList<>();
        for (Match m : matches) {
            Term x = a.terms().get(m.source);
            Term y = b.terms().get(m.target);
            switch (m.kind) {
                case LITERAL -> shared.add(x.word());
                case ABBREVIATION -> {
                    if (x.expanded()) {
                        reasons.add(x.original() + " is recognized as " + x.word() + ".");
                    }
                    if (y.expanded()) {
                        reasons.add(y.original() + " is recognized as " + y.word() + ".");
                    }
                    if (!x.expanded() && !y.expanded()) {
                        shared.add(x.word());
                    }
                }
                case SYNONYM -> reasons.add(x.word() + " and " + y.word() + " are treated as the same word.");
                case WEAK_SYNONYM -> reasons.add(x.word() + " and " + y.word()
                        + " are only loosely related, so this match needs confirmation.");
            }
        }
        if (!shared.isEmpty()) {
            reasons.add(0, "Both names contain " + String.join(", ", shared) + ".");
        }
        if (tier == EvidenceTier.REORDERED_WORDS) {
            reasons.add("The same words appear in a different order.");
        }
        if (tier == EvidenceTier.PARTIAL) {
            leftovers(a, matches, true).forEach(w -> reasons.add("The source name also has " + w
                    + ", which has no counterpart in the target name."));
            leftovers(b, matches, false).forEach(w -> reasons.add("The target name also has " + w
                    + ", which has no counterpart in the source name."));
            reasons.add("Only part of the name matches, so the meaning needs to be confirmed.");
        }
        if (tier == EvidenceTier.SIMILARITY_ONLY && similarity >= 0.5) {
            reasons.add("The names are spelled similarly (" + Math.round(similarity * 100)
                    + "%) but share no word; similar spelling alone is not enough evidence.");
        }
        return reasons;
    }

    private static List<String> leftovers(NameProfile p, List<Match> matches, boolean source) {
        Set<Integer> used = new java.util.HashSet<>();
        matches.forEach(m -> used.add(source ? m.source : m.target));
        List<String> words = new ArrayList<>();
        for (int i = 0; i < p.terms().size(); i++) {
            if (!used.contains(i)) {
                words.add(p.terms().get(i).word());
            }
        }
        return words;
    }

    // ------------------------------------------------------------------ similarity

    static double levenshteinSimilarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 0 : 1.0 - (double) levenshtein(a, b) / max;
    }

    /** Classic edit distance (insert, delete, substitute), two-row implementation. */
    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Average best word-to-word spelling similarity, measured in both directions. */
    private static double tokenSimilarity(NameProfile a, NameProfile b) {
        return (directional(a.terms(), b.terms()) + directional(b.terms(), a.terms())) / 2.0;
    }

    private static double directional(List<Term> from, List<Term> to) {
        double sum = 0;
        for (Term x : from) {
            double best = 0;
            for (Term y : to) {
                best = Math.max(best, levenshteinSimilarity(x.word(), y.word()));
            }
            sum += best;
        }
        return from.isEmpty() ? 0 : sum / from.size();
    }
}
