package com.datasphere.migration.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** An entry in the in-memory recent-activity feed. */
public record ActivityEvent(Instant timestamp, String type, String message, String result, String jobId) {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Timestamp formatted in the server's time zone for display. */
    public String displayTime() {
        return DISPLAY.format(timestamp);
    }
}
