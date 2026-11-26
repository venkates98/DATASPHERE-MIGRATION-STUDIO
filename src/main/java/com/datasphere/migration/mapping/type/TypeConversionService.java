package com.datasphere.migration.mapping.type;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.adapter.DatabaseAdapters;
import com.datasphere.migration.db.dialect.NativeTypeSpec;
import com.datasphere.migration.metadata.ColumnMetadata;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Central, vendor-pair-independent conversion: normalise the source type, then ask the target dialect
 * for a native type. Compatibility against an existing target column still uses {@link TypeCompatibilityChecker}.
 */
@Service
public class TypeConversionService {

    public ConversionResult propose(ColumnMetadata sourceColumn, DatabaseType sourceDb, DatabaseType targetDb) {
        return propose(TypeNormalizer.normalize(sourceColumn, sourceDb), sourceDb, targetDb);
    }

    public ConversionResult propose(NormalizedType source, DatabaseType sourceDb, DatabaseType targetDb) {
        NativeTypeSpec spec = DatabaseAdapters.require(targetDb).dialect().mapType(source);
        List<String> warnings = new ArrayList<>(spec.warnings());
        if (spec.unsupported()) {
            return new ConversionResult(sourceDb, targetDb, source.display(), spec, ConversionStatus.UNSUPPORTED,
                    true, List.copyOf(warnings), spec.unsupportedReason());
        }
        ConversionStatus status = spec.potentialDataLoss() || !warnings.isEmpty()
                ? ConversionStatus.CONDITIONAL : ConversionStatus.COMPATIBLE;
        if (sourceDb == targetDb && source.nativeName().equalsIgnoreCase(spec.nativeName())) {
            warnings.add(0, "Source and target are the same product; the native type is preserved where possible.");
        }
        return new ConversionResult(sourceDb, targetDb, source.display(), spec, status, spec.potentialDataLoss(),
                List.copyOf(warnings), null);
    }
}
