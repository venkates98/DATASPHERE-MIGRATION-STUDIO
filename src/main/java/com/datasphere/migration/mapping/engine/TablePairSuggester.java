package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.config.MappingProperties;
import com.datasphere.migration.metadata.TableSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Proposes which source table corresponds to which target table, using only table names. The result is a
 * suggestion for the user to confirm; nothing is selected automatically.
 */
public final class TablePairSuggester {

    private static final Set<String> NOISE_PREFIXES = Set.of("TBL", "TB", "T", "TABLE", "TAB");
    private static final double MINIMUM_SCORE = 60.0;
    private static final double CLEAR_GAP = 10.0;

    /** @param score 0..100 name-evidence score */
    public record Suggestion(String sourceTable, String targetTable, double score, List<String> reasons) {
    }

    private final MappingProperties properties;
    private final NameMatcher matcher;

    public TablePairSuggester(MappingProperties properties, NameMatcher matcher) {
        this.properties = properties;
        this.matcher = matcher;
    }

    public List<Suggestion> suggest(List<TableSummary> sources, List<TableSummary> targets) {
        int ns = sources.size();
        int nt = targets.size();
        NameProfile[] sp = new NameProfile[ns];
        NameProfile[] tp = new NameProfile[nt];
        for (int i = 0; i < ns; i++) {
            sp[i] = matcher.profile(stripNoise(sources.get(i).name()));
        }
        for (int j = 0; j < nt; j++) {
            tp[j] = matcher.profile(stripNoise(targets.get(j).name()));
        }
        double nameWeights = properties.weights().exact() + properties.weights().synonym()
                + properties.weights().similarity();
        double[][] score = new double[ns][nt];
        NameEvidence[][] evidence = new NameEvidence[ns][nt];
        for (int i = 0; i < ns; i++) {
            for (int j = 0; j < nt; j++) {
                NameEvidence e = matcher.compare(sp[i], tp[j]);
                evidence[i][j] = e;
                score[i][j] = e.tier().canSuggest()
                        ? e.points(properties.weights(), properties.thresholds()).total() / nameWeights * 100.0 : 0;
            }
        }
        List<Suggestion> result = new ArrayList<>();
        for (int i = 0; i < ns; i++) {
            int best = bestIndex(score[i]);
            if (best < 0 || score[i][best] < MINIMUM_SCORE || gapBelow(score[i], best)) {
                continue;
            }
            double[] column = new double[ns];
            for (int k = 0; k < ns; k++) {
                column[k] = score[k][best];
            }
            if (bestIndex(column) != i || gapBelow(column, i)) {
                continue;
            }
            result.add(new Suggestion(sources.get(i).name(), targets.get(best).name(),
                    Math.round(score[i][best] * 10.0) / 10.0, evidence[i][best].reasons()));
        }
        result.sort(Comparator.comparing(Suggestion::sourceTable, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static int bestIndex(double[] values) {
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            if (best < 0 || values[i] > values[best]) {
                best = i;
            }
        }
        return best < 0 || values[best] <= 0 ? -1 : best;
    }

    private static boolean gapBelow(double[] values, int best) {
        double second = 0;
        for (int i = 0; i < values.length; i++) {
            if (i != best) {
                second = Math.max(second, values[i]);
            }
        }
        return values[best] - second < CLEAR_GAP;
    }

    private static String stripNoise(String tableName) {
        List<String> tokens = new ArrayList<>(NameNormalizer.tokens(tableName));
        if (tokens.size() > 1 && NOISE_PREFIXES.contains(tokens.get(0))) {
            tokens.remove(0);
        }
        return String.join("_", tokens);
    }
}
