package com.datasphere.migration.dto;

/** Display-only information about the signed-in user. Contains no credentials. */
public record CurrentUser(String username, String displayName, String initials) {
}
