package com.datasphere.migration.project;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * One re-entrant lock per project so concurrent requests (two browser tabs, a double click) cannot interleave
 * a read-modify-write of the same project. Different projects never block each other.
 */
@Component
public class ProjectLocks {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String projectId, Supplier<T> work) {
        ReentrantLock lock = locks.computeIfAbsent(projectId, k -> new ReentrantLock());
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
