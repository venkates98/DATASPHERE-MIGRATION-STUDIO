package com.datasphere.migration.project;

import java.time.Instant;

/** One entry of a project's state history. */
public record StateTransition(Instant at, ProjectState from, ProjectState to, String reason) {
}
