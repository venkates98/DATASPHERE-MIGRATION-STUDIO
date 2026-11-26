package com.datasphere.migration.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Identifies a table by schema and name. {@code schema} is empty when the database has no schema level. */
public record TableRef(
        @Size(max = 256) @Pattern(regexp = "[^\\p{Cntrl}]*", message = "Invalid schema name.") String schema,
        @NotBlank @Size(max = 256) @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Invalid table name.") String name) {

    public TableRef {
        schema = schema == null ? "" : schema;
    }

    /** Stable map key, e.g. {@code HR.EMPLOYEE}. */
    public String key() {
        return schema.isEmpty() ? name : schema + "." + name;
    }
}
