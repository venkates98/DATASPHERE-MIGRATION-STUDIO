package com.datasphere.migration.mapping.type;

/** Vendor-neutral family of a column data type. Every database's native types are first reduced to one of these. */
public enum TypeCategory {
    INTEGER("whole number"),
    DECIMAL("decimal number"),
    FLOAT("floating-point number"),
    STRING("text"),
    CLOB("long text"),
    BINARY("binary data"),
    BLOB("large binary data"),
    DATE("date"),
    TIME("time"),
    TIMESTAMP("date and time"),
    TIMESTAMP_TZ("date and time with time zone"),
    BOOLEAN("yes/no value"),
    UUID("unique identifier"),
    XML("XML document"),
    JSON("JSON document"),
    OTHER("special type");

    private final String plainName;

    TypeCategory(String plainName) {
        this.plainName = plainName;
    }

    /** Everyday wording used in validation messages. */
    public String plainName() {
        return plainName;
    }

    public boolean isExactNumeric() {
        return this == INTEGER || this == DECIMAL;
    }

    public boolean isNumeric() {
        return isExactNumeric() || this == FLOAT;
    }

    public boolean isText() {
        return this == STRING || this == CLOB;
    }

    public boolean isBinary() {
        return this == BINARY || this == BLOB;
    }

    public boolean isDateTime() {
        return this == DATE || this == TIMESTAMP || this == TIMESTAMP_TZ;
    }
}
