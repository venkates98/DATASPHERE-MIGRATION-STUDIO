package com.datasphere.migration.dto;

/** A dashboard shortcut. When disabled, {@code disabledReason} explains why. */
public record QuickAction(String label, String icon, String href, boolean enabled, String disabledReason) {
}
