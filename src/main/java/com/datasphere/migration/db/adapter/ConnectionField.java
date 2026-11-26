package com.datasphere.migration.db.adapter;

/** One input on the connection form. The UI shows only the fields the selected adapter declares. */
public record ConnectionField(String id, String label, Kind kind, boolean required, String placeholder,
                              String help) {

    public enum Kind {
        TEXT, PASSWORD, NUMBER, CHECKBOX
    }
}
