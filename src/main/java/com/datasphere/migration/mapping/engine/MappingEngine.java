package com.datasphere.migration.mapping.engine;

import com.datasphere.migration.config.MappingProperties;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.mapping.model.Candidate;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.model.MappingStatus;
import com.datasphere.migration.mapping.type.CompatibilityLevel;
import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.Transformation;
import com.datasphere.migration.mapping.type.TypeNormalizer;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic column-mapping engine. It works for any source and target database because it compares
 * vendor-neutral {@link NormalizedType}s and column names; there is no AI, network call or randomness, so
 * identical metadata always yields identical results.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>Only columns present in the supplied metadata are ever mapped (nothing is invented).</li>
 *   <li>One source column maps to at most one target column and vice versa.</li>
 *   <li>Incompatible type pairs, name-less (spelling-only) matches and weak scores are never auto-selected.</li>
 *   <li>If the best two candidates are closer than the ambiguity gap, nothing is selected and the column is
 *       reported as ambiguous for manual choice.</li>
 * </ul>
 */
public final class MappingEngine {

    private static final int MAX_CANDIDATES = 5;
    private static final double CANDIDATE_FLOOR = 20.0;

    private final MappingProperties properties;
    private final NameMatcher matcher;
    private final MappingScorer scorer;

    public MappingEngine(MappingProperties properties, AbbreviationDictionary dictionary) {
        this.properties = properties;
        this.matcher = new NameMatcher(dictionary);
        this.scorer = new MappingScorer(properties);
    }

    public NameMatcher nameMatcher() {
        return matcher;
    }

    /** One {@link ColumnMapping} per source column, in source column order. */
    public List<ColumnMapping> suggest(TableMetadata source, TableMetadata target, DatabaseType sourceDb,
                                       DatabaseType targetDb) {
        List<ColumnMetadata> sc = source.columns();
        List<ColumnMetadata> tc = target.columns();
        int ns = sc.size();
        int nt = tc.size();
        double gap = properties.thresholds().ambiguityGap();

        NameProfile[] sn = new NameProfile[ns];
        NormalizedType[] st = new NormalizedType[ns];
        for (int i = 0; i < ns; i++) {
            sn[i] = matcher.profile(sc.get(i).name());
            st[i] = TypeNormalizer.normalize(sc.get(i), sourceDb);
        }
        NameProfile[] tn = new NameProfile[nt];
        NormalizedType[] tt = new NormalizedType[nt];
        for (int j = 0; j < nt; j++) {
            tn[j] = matcher.profile(tc.get(j).name());
            tt[j] = TypeNormalizer.normalize(tc.get(j), targetDb);
        }

        ScoreBreakdown[][] scores = new ScoreBreakdown[ns][nt];
        for (int i = 0; i < ns; i++) {
            for (int j = 0; j < nt; j++) {
                scores[i][j] = scorer.score(sc.get(i), tc.get(j), st[i], tt[j], matcher.compare(sn[i], tn[j]),
                        Transformation.NONE);
            }
        }

        int[] assigned = assign(scores, ns, nt, gap);
        boolean[] taken = new boolean[nt];
        for (int a : assigned) {
            if (a >= 0) {
                taken[a] = true;
            }
        }

        List<ColumnMapping> result = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            ColumnMapping mapping = new ColumnMapping(sc.get(i).name());
            mapping.setCandidates(candidates(scores[i], tc));
            if (assigned[i] >= 0) {
                fillAssigned(mapping, scores[i][assigned[i]], tc.get(assigned[i]).name());
            } else {
                fillUnassigned(mapping, scores[i], tc, taken);
            }
            result.add(mapping);
        }
        return result;
    }

    /** Scores a specific pair, for example after the user picked a target by hand. */
    public ScoreBreakdown scorePair(ColumnMetadata source, ColumnMetadata target, DatabaseType sourceDb,
                                    DatabaseType targetDb, Transformation transformation) {
        return scorer.score(source, target, TypeNormalizer.normalize(source, sourceDb),
                TypeNormalizer.normalize(target, targetDb), matcher.compare(matcher.profile(source.name()),
                        matcher.profile(target.name())), transformation);
    }

    /** Copies a score breakdown into a mapping's explanatory fields. */
    public static void applyScore(ColumnMapping mapping, ScoreBreakdown b) {
        mapping.setScore(b.total());
        mapping.setConfidence(b.confidence());
        mapping.setEvidence(b.tier());
        mapping.setCompatibility(b.compatibility().level());
        mapping.setReasons(b.reasons());
        mapping.setWarnings(b.warnings());
    }

    // ------------------------------------------------------------------ assignment

    private boolean eligible(ScoreBreakdown b) {
        return b.total() >= properties.thresholds().minimumSuggestion()
                && b.tier().canSuggest()
                && !b.compatibility().isIncompatible();
    }

    /**
     * Pairs columns in rounds. A pair is settled only when it is the clear winner from both sides: the target
     * is the source's best remaining candidate by at least {@code gap} points, and the source is that target's
     * best remaining candidate by at least {@code gap} points. Settled columns leave the pool and the next
     * round re-evaluates the rest; whatever never becomes clear is left for the user.
     */
    private int[] assign(ScoreBreakdown[][] scores, int ns, int nt, double gap) {
        int[] assigned = new int[ns];
        Arrays.fill(assigned, -1);
        boolean[] sourceFree = new boolean[ns];
        boolean[] targetFree = new boolean[nt];
        Arrays.fill(sourceFree, true);
        Arrays.fill(targetFree, true);

        boolean progress = true;
        while (progress) {
            progress = false;
            List<int[]> settled = new ArrayList<>();
            for (int i = 0; i < ns; i++) {
                if (!sourceFree[i]) {
                    continue;
                }
                int best = -1;
                double bestScore = -1;
                double second = -1;
                for (int j = 0; j < nt; j++) {
                    if (targetFree[j] && eligible(scores[i][j])) {
                        double s = scores[i][j].total();
                        if (s > bestScore) {
                            second = bestScore;
                            bestScore = s;
                            best = j;
                        } else if (s > second) {
                            second = s;
                        }
                    }
                }
                if (best < 0 || (second >= 0 && bestScore - second < gap)) {
                    continue;
                }
                int bestSource = -1;
                double bestForTarget = -1;
                double secondForTarget = -1;
                for (int k = 0; k < ns; k++) {
                    if (sourceFree[k] && eligible(scores[k][best])) {
                        double s = scores[k][best].total();
                        if (s > bestForTarget) {
                            secondForTarget = bestForTarget;
                            bestForTarget = s;
                            bestSource = k;
                        } else if (s > secondForTarget) {
                            secondForTarget = s;
                        }
                    }
                }
                if (bestSource == i && !(secondForTarget >= 0 && bestForTarget - secondForTarget < gap)) {
                    settled.add(new int[]{i, best});
                }
            }
            for (int[] pair : settled) {
                assigned[pair[0]] = pair[1];
                sourceFree[pair[0]] = false;
                targetFree[pair[1]] = false;
                progress = true;
            }
        }
        return assigned;
    }

    private void fillAssigned(ColumnMapping mapping, ScoreBreakdown b, String targetName) {
        mapping.setTargetColumn(targetName);
        applyScore(mapping, b);
        boolean clean = b.tier().isFullyResolved()
                && (b.compatibility().level() == CompatibilityLevel.IDENTICAL
                || b.compatibility().level() == CompatibilityLevel.COMPATIBLE)
                && b.warnings().isEmpty();
        mapping.setStatus(clean ? MappingStatus.SUGGESTED : MappingStatus.NEEDS_REVIEW);
        mapping.setOrigin(ColumnMapping.ORIGIN_ENGINE);
    }

    private void fillUnassigned(ColumnMapping mapping, ScoreBreakdown[] row, List<ColumnMetadata> targets,
                                boolean[] taken) {
        List<Candidate> viable = new ArrayList<>();
        String claimedElsewhere = null;
        for (int j = 0; j < row.length; j++) {
            if (eligible(row[j])) {
                if (taken[j]) {
                    claimedElsewhere = claimedElsewhere == null ? targets.get(j).name() : claimedElsewhere;
                } else {
                    viable.add(new Candidate(targets.get(j).name(), row[j].total(), row[j].confidence(),
                            row[j].compatibility().level(), null));
                }
            }
        }
        mapping.setOrigin(ColumnMapping.ORIGIN_ENGINE);
        mapping.setCompatibility(null);
        if (viable.isEmpty()) {
            mapping.setStatus(MappingStatus.UNMAPPED);
            if (claimedElsewhere != null) {
                mapping.setReasons(List.of("The most likely target column (" + claimedElsewhere
                        + ") is a better match for another source column, so it was not used here."));
            } else {
                mapping.setReasons(List.of(mapping.getCandidates().isEmpty()
                        ? "No target column matched well enough to suggest a mapping."
                        : "No target column was convincing enough to suggest automatically. "
                                + "The closest weak candidates are listed in the dropdown."));
            }
            return;
        }
        viable.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<String> listing = viable.stream().limit(3)
                .map(c -> c.targetColumn() + " (" + String.format(Locale.ROOT, "%.1f", c.score()) + ")").toList();
        mapping.setStatus(MappingStatus.AMBIGUOUS);
        mapping.setScore(viable.get(0).score());
        mapping.setConfidence(viable.get(0).confidence());
        mapping.setReasons(List.of("Several columns look about equally likely: " + String.join(", ", listing)
                + ". Nothing was selected automatically; choose the correct target."));
    }

    private List<Candidate> candidates(ScoreBreakdown[] row, List<ColumnMetadata> targets) {
        List<Candidate> list = new ArrayList<>();
        for (int j = 0; j < row.length; j++) {
            ScoreBreakdown b = row[j];
            if (b.tier() != EvidenceTier.NONE && b.total() >= CANDIDATE_FLOOR) {
                Transformation suggested = b.compatibility().suggestedTransformation();
                list.add(new Candidate(targets.get(j).name(), b.total(), b.confidence(), b.compatibility().level(),
                        suggested == null ? null : suggested.name()));
            }
        }
        // stable sort keeps target column order for equal scores
        list.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return list.size() > MAX_CANDIDATES ? new ArrayList<>(list.subList(0, MAX_CANDIDATES)) : list;
    }
}
