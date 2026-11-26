package com.datasphere.migration.metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Discovered structure of one table or view: columns, keys and constraints. */
public record TableMetadata(String schema, String name, String tableType, List<ColumnMetadata> columns) {

    public TableMetadata {
        schema = schema == null ? "" : schema;
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public TableRef ref() {
        return new TableRef(schema, name);
    }

    public boolean isView() {
        return tableType != null && tableType.toUpperCase().contains("VIEW");
    }

    public Optional<ColumnMetadata> column(String columnName) {
        return columns.stream().filter(c -> c.name().equals(columnName)).findFirst();
    }

    public List<ColumnMetadata> primaryKeyColumns() {
        return columns.stream().filter(ColumnMetadata::primaryKey).toList();
    }

    /**
     * SHA-256 over the structural facts that influence a mapping (names, types, sizes, nullability, keys).
     * Column order and default values are ignored so cosmetic changes do not invalidate an approval.
     */
    public String fingerprint() {
        String canonical = columns.stream()
                .sorted(Comparator.comparing(ColumnMetadata::name))
                .map(c -> String.join("|", c.name(), String.valueOf(c.typeName()).toUpperCase(),
                        String.valueOf(c.size()), String.valueOf(c.scale()), String.valueOf(c.nullable()),
                        String.valueOf(c.primaryKey()), String.valueOf(c.unique()), String.valueOf(c.foreignKey()),
                        String.valueOf(c.autoIncrement()), String.valueOf(c.hasDefault())))
                .collect(Collectors.joining("\n", schema + "." + name + "\n", ""));
        return sha256(canonical);
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
