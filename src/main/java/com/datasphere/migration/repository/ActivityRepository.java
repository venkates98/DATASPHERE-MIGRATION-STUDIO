package com.datasphere.migration.repository;

import com.datasphere.migration.dto.ActivityEvent;
import java.util.List;

/** Recent migration activity shown on the dashboard. */
public interface ActivityRepository {

    void add(ActivityEvent event);

    /** Most recent first. */
    List<ActivityEvent> recent(int limit);

    void clear();
}
