package uk.iwaservice.classloadout.resupply;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live (non-persistent) count of active resupply packs per owner, combined
 * across health and ammo packs. Backs {@code maxActivePacksPerPlayer}; a
 * server restart or chunk unload simply drops the count back down, which is
 * fine since the packs themselves are short-lived by design.
 */
public final class ResupplyPackRegistry {

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

    private ResupplyPackRegistry() {}
}
