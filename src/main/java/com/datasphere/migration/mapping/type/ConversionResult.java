package com.datasphere.migration.mapping.type;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.dialect.NativeTypeSpec;
import java.util.List;

/**
 * Proposed native type on the target for a source column. Never silently substitutes an unrelated type:
 * {@link ConversionStatus#UNSUPPORTED} means the user must choose a conversion or leave the column unmapped.
 */
public record ConversionResult(
        DatabaseType sourceDatabase,
        DatabaseType targetDatabase,
        String sourceDisplay,
        NativeTypeSpec targetType,
        ConversionStatus status,
        boolean potentialDataLoss,
        List<String> warnings,
        String unsupportedReason) {

    public boolean supported() {
        return status != ConversionStatus.UNSUPPORTED;
    }
}
