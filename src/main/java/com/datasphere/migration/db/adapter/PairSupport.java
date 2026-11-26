package com.datasphere.migration.db.adapter;

import java.util.List;

/** One cell of the generated compatibility matrix. */
public record PairSupport(
        String sourceId,
        String sourceLabel,
        String targetId,
        String targetLabel,
        String overall,
        boolean connection,
        boolean discovery,
        boolean mapping,
        boolean conversion,
        boolean schemaSql,
        boolean dataMigration,
        boolean validation,
        List<String> unsupported,
        List<String> limitations) {
}
