package com.datasphere.migration.mapping.model;

import com.datasphere.migration.mapping.engine.ConfidenceClass;
import com.datasphere.migration.mapping.engine.EvidenceTier;
import com.datasphere.migration.mapping.type.CompatibilityLevel;
import com.datasphere.migration.mapping.type.Transformation;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The mapping decision for one source column. A source column maps to at most one target column; the
 * engine never fans one source out to several targets or merges several sources into one target.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class ColumnMapping {

    public static final String ORIGIN_ENGINE = "ENGINE";
    public static final String ORIGIN_USER = "USER";

    private String sourceColumn;
    private String targetColumn;
    private double score;
    private ConfidenceClass confidence = ConfidenceClass.WEAK;
    private MappingStatus status = MappingStatus.UNMAPPED;
    private EvidenceTier evidence = EvidenceTier.NONE;
    private CompatibilityLevel compatibility;
    private List<String> reasons = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Transformation transformation = Transformation.NONE;
    private String transformationParameter;
    private List<Candidate> candidates = new ArrayList<>();
    private String origin = ORIGIN_ENGINE;
    private String rejectedTarget;
    private Instant decidedAt;

    public ColumnMapping() {
        // for Jackson
    }

    public ColumnMapping(String sourceColumn) {
        this.sourceColumn = sourceColumn;
    }

    public String getSourceColumn() {
        return sourceColumn;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
        this.targetColumn = targetColumn;
    }

    public boolean isMapped() {
        return targetColumn != null;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public ConfidenceClass getConfidence() {
        return confidence;
    }

    public void setConfidence(ConfidenceClass confidence) {
        this.confidence = confidence;
    }

    public MappingStatus getStatus() {
        return status;
    }

    public void setStatus(MappingStatus status) {
        this.status = status;
    }

    public EvidenceTier getEvidence() {
        return evidence;
    }

    public void setEvidence(EvidenceTier evidence) {
        this.evidence = evidence;
    }

    public CompatibilityLevel getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(CompatibilityLevel compatibility) {
        this.compatibility = compatibility;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = new ArrayList<>(reasons);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = new ArrayList<>(warnings);
    }

    public Transformation getTransformation() {
        return transformation == null ? Transformation.NONE : transformation;
    }

    public void setTransformation(Transformation transformation) {
        this.transformation = transformation == null ? Transformation.NONE : transformation;
    }

    public String getTransformationParameter() {
        return transformationParameter;
    }

    public void setTransformationParameter(String transformationParameter) {
        this.transformationParameter = transformationParameter;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = new ArrayList<>(candidates);
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRejectedTarget() {
        return rejectedTarget;
    }

    public void setRejectedTarget(String rejectedTarget) {
        this.rejectedTarget = rejectedTarget;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
