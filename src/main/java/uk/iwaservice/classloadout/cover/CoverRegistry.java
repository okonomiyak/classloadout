package uk.iwaservice.classloadout.cover;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live (non-persistent) count of active covers per owner, backing
 * {@code maxActiveCoversPerPlayer}. Separate from
 * {@link uk.iwaservice.classloadout.resupply.ResupplyPackRegistry} since
 * covers are a different kind of prop (defensive structure, not a
 * consumable) with their own limit.
 */
public final class CoverRegistry {

    private static final Map<UUID, AtomicInteger> ACTIVE = new ConcurrentHashMap<>();

    public static void register(UUID owner) {
        ACTIVE.computeIfAbsent(owner, id -> new AtomicInteger()).incrementAndGet();
    }

    public static void unregister(UUID owner) {
        AtomicInteger count = ACTIVE.get(owner);
        if (count != null && count.decrementAndGet() <= 0) {
            ACTIVE.remove(owner, count);
        }
    }

    public static int countActive(UUID owner) {
        AtomicInteger count = ACTIVE.get(owner);
        return count == null ? 0 : count.get();
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private CoverRegistry() {}
}
