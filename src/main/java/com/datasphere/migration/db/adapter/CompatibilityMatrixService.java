package com.datasphere.migration.db.adapter;

import com.datasphere.migration.db.dialect.NativeTypeSpec;
import com.datasphere.migration.mapping.type.NormalizedType;
import com.datasphere.migration.mapping.type.TypeCategory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Builds the source×target matrix from registered adapters and dialect type maps. A pair is never marked
 * fully migration-ready solely because both adapters exist: data movement is tested against H2 stand-ins,
 * not live vendor servers.
 */
@Service
public class CompatibilityMatrixService {

    public List<DatabaseCatalogEntry> catalog() {
        List<DatabaseCatalogEntry> list = new ArrayList<>();
        for (DatabaseAdapter adapter : DatabaseAdapters.all()) {
            list.add(new DatabaseCatalogEntry(adapter.type().name(), adapter.displayName(), adapter.driverClassName(),
                    adapter.driverAvailable(), adapter.defaultPort(), adapter.databaseFieldLabel(),
                    adapter.databaseIsSchema(), adapter.connectionFields(), adapter.capabilities(),
                    adapter.dialect().unsupportedTypeNotes()));
        }
        return list;
    }

    public List<PairSupport> matrix() {
        List<PairSupport> cells = new ArrayList<>();
        for (DatabaseAdapter source : DatabaseAdapters.all()) {
            for (DatabaseAdapter target : DatabaseAdapters.all()) {
                cells.add(cell(source, target));
            }
        }
        return cells;
    }

    private PairSupport cell(DatabaseAdapter source, DatabaseAdapter target) {
        boolean drivers = source.driverAvailable() && target.driverAvailable();
        List<String> unsupported = new ArrayList<>(target.dialect().unsupportedTypeNotes());
        for (TypeCategory category : TypeCategory.values()) {
            if (category == TypeCategory.OTHER) {
                NativeTypeSpec spec = target.dialect().mapType(NormalizedType.of(category, "OTHER"));
                if (spec.unsupported()) {
                    unsupported.add("Category OTHER is unsupported unless the native type names match.");
                }
            }
        }
        List<String> limits = new ArrayList<>();
        limits.addAll(source.capabilities().limitations());
        limits.addAll(target.capabilities().limitations());
        limits.add("Data movement is implemented and tested with in-memory H2 databases in this product's "
                + "compatibility mode, not against a live " + source.displayName() + " or " + target.displayName()
                + " server.");
        String overall = drivers ? "PARTIAL SUPPORT" : "UNSUPPORTED";
        return new PairSupport(source.type().name(), source.displayName(), target.type().name(), target.displayName(),
                overall, drivers, true, true, true, true, drivers, true, List.copyOf(unsupported),
                List.copyOf(limits));
    }

    public record DatabaseCatalogEntry(
            String id,
            String label,
            String driverClass,
            boolean driverAvailable,
            int defaultPort,
            String databaseLabel,
            boolean databaseIsSchema,
            List<ConnectionField> fields,
            AdapterCapabilities capabilities,
            List<String> unsupportedTypeNotes) {
    }
}
