package uk.iwaservice.classloadout.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side mirror of the preset roster, the per-slot whitelists, and the
 * local player's own personal loadout, fed exclusively by
 * {@link LoadoutSyncPacket}. Read by the preset editor (OP), the whitelist
 * editor (OP) and the loadout screen (everyone).
 */
public final class LoadoutClientData {

    private static final LoadoutSyncPacket.Whitelists EMPTY_WHITELISTS =
            new LoadoutSyncPacket.Whitelists(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of());

    private static List<LoadoutSyncPacket.Entry> classes = List.of();
    private static LoadoutSyncPacket.PersonalData personal =
            new LoadoutSyncPacket.PersonalData(null, null, null, null, null, null, null, null, null, null);
    private static LoadoutSyncPacket.Whitelists whitelists = EMPTY_WHITELISTS;
    private static Map<LoadoutSlot, Map<ResourceLocation, Map<ResourceLocation, Integer>>> ammoGrants = Map.of();
    private static Map<ResourceLocation, CompoundTag> itemVariants = Map.of();
    private static Map<ResourceLocation, Long> variantRegisteredAt = Map.of();
    private static List<ResourceLocation> protectedItems = List.of();
    private static Map<ResourceLocation, Integer> spawnKit = Map.of();
    private static List<ResourceLocation> hammerBlocks = List.of();
    /** Slots an OP has locked on the local player's own loadout (see {@code LoadoutManager#lockSlot}) - never someone else's. */
    private static Set<LoadoutSlot> lockedSlots = Set.of();
    /** Incremented on every sync; lets screens detect updates cheaply. */
    private static int revision;

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized void applySync(List<LoadoutSyncPacket.Entry> newClasses,
                                              LoadoutSyncPacket.PersonalData newPersonal,
                                              LoadoutSyncPacket.Whitelists newWhitelists,
                                              List<LoadoutSyncPacket.AmmoGrantEntry> newAmmoGrants,
                                              List<LoadoutSyncPacket.VariantEntry> newVariants,
                                              List<ResourceLocation> newProtectedItems,
                                              List<LoadoutSyncPacket.SpawnKitEntry> newSpawnKit,
                                              List<ResourceLocation> newHammerBlocks,
                                              List<LoadoutSlot> newLockedSlots) {
        classes = List.copyOf(newClasses);
        personal = newPersonal;
        whitelists = newWhitelists;
        Map<LoadoutSlot, Map<ResourceLocation, Map<ResourceLocation, Integer>>> grants = new EnumMap<>(LoadoutSlot.class);
        for (LoadoutSyncPacket.AmmoGrantEntry g : newAmmoGrants) {
            grants.computeIfAbsent(g.slot(), s -> new HashMap<>())
                    .computeIfAbsent(g.item(), i -> new LinkedHashMap<>())
                    .put(g.ammoItem(), g.count());
        }
        ammoGrants = grants;
        Map<ResourceLocation, CompoundTag> variants = new HashMap<>();
        Map<ResourceLocation, Long> registeredAt = new HashMap<>();
        for (LoadoutSyncPacket.VariantEntry v : newVariants) {
            variants.put(v.id(), v.stack());
            registeredAt.put(v.id(), v.registeredAt());
        }
        itemVariants = variants;
        variantRegisteredAt = registeredAt;
        protectedItems = List.copyOf(newProtectedItems);
        Map<ResourceLocation, Integer> kit = new HashMap<>();
        for (LoadoutSyncPacket.SpawnKitEntry s : newSpawnKit) {
            kit.put(s.item(), s.count());
        }
        spawnKit = kit;
        hammerBlocks = List.copyOf(newHammerBlocks);
        lockedSlots = newLockedSlots.isEmpty() ? Set.of() : EnumSet.copyOf(newLockedSlots);
        revision++;
    }

    public static synchronized void clear() {
        classes = List.of();
        personal = new LoadoutSyncPacket.PersonalData(null, null, null, null, null, null, null, null, null, null);
        whitelists = EMPTY_WHITELISTS;
        ammoGrants = Map.of();
        itemVariants = Map.of();
        variantRegisteredAt = Map.of();
        protectedItems = List.of();
        spawnKit = Map.of();
        hammerBlocks = List.of();
        lockedSlots = Set.of();
        revision++;
    }

    public static synchronized List<LoadoutSyncPacket.Entry> getClasses() {
        return new ArrayList<>(classes);
    }

    @Nullable
    public static synchronized LoadoutSyncPacket.Entry getById(UUID id) {
        for (LoadoutSyncPacket.Entry e : classes) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    public static synchronized LoadoutSyncPacket.PersonalData getPersonal() {
        return personal;
    }

    public static synchronized List<ResourceLocation> getWhitelist(LoadoutSlot slot) {
        return whitelists.get(slot);
    }

    /** Never null - an item with no ammo grants yet returns an empty map. */
    public static synchronized Map<ResourceLocation, Integer> getAmmoGrants(LoadoutSlot slot, ResourceLocation item) {
        Map<ResourceLocation, Map<ResourceLocation, Integer>> bySlot = ammoGrants.get(slot);
        Map<ResourceLocation, Integer> grants = bySlot == null ? null : bySlot.get(item);
        return grants == null ? Map.of() : grants;
    }

    /** Client-side mirror of {@code LoadoutManager.getItemVariants()}; used to resolve slot/whitelist ids back into real ItemStacks. */
    public static synchronized Map<ResourceLocation, CompoundTag> getItemVariants() {
        return itemVariants;
    }

    /** Epoch-millis registration time for a held-item variant id, or 0 if unknown. */
    public static synchronized long getVariantRegisteredAt(ResourceLocation id) {
        return variantRegisteredAt.getOrDefault(id, 0L);
    }

    public static synchronized List<ResourceLocation> getProtectedItems() {
        return protectedItems;
    }

    public static synchronized Map<ResourceLocation, Integer> getSpawnKit() {
        return spawnKit;
    }

    public static synchronized List<ResourceLocation> getHammerBlocks() {
        return hammerBlocks;
    }

    /** True if an OP has locked this slot on the local player's own loadout - see {@code LoadoutManager#lockSlot}. */
    public static synchronized boolean isLocked(LoadoutSlot slot) {
        return lockedSlots.contains(slot);
    }

    private LoadoutClientData() {}
}
