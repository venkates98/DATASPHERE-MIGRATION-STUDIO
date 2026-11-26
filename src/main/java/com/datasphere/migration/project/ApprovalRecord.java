package com.datasphere.migration.project;

import java.time.Instant;

/**
 * Evidence that a user approved a specific version of the mapping against a specific database structure.
 * The migration gate trusts nothing but this record: it must match the current mapping version, the current
 * mapping content hash and the live metadata fingerprint.
 */
public record ApprovalRecord(
        Instant approvedAt,
        String approvedBy,
        String projectId,
        String projectName,
        int mappingVersion,
        String mappingHash,
        String metadataFingerprint,
        String sourceDatabase,
        String targetDatabase) {
}
