package com.datasphere.migration.repository.memory;

import com.datasphere.migration.dto.ActivityEvent;
import com.datasphere.migration.repository.ActivityRepository;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

/**
 * Bounded, thread-safe in-memory activity feed. The oldest entries are evicted beyond {@value #CAPACITY}.
 */
@Repository
public class InMemoryActivityRepository implements ActivityRepository {

    static final int CAPACITY = 200;

    private final ConcurrentLinkedDeque<ActivityEvent> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();

    @Override
    public void add(ActivityEvent event) {
        events.addFirst(event);
        if (size.incrementAndGet() > CAPACITY) {
            if (events.pollLast() != null) {
                size.decrementAndGet();
            }
        }
    }

    @Override
    public List<ActivityEvent> recent(int limit) {
        List<ActivityEvent> result = new ArrayList<>(Math.min(limit, CAPACITY));
        Iterator<ActivityEvent> it = events.iterator();
        while (it.hasNext() && result.size() < limit) {
            result.add(it.next());
        }
        return List.copyOf(result);
    }

    @Override
    public void clear() {
        events.clear();
        size.set(0);
    }
}
