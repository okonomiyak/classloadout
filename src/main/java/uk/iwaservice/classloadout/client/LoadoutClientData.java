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
    /** Purely organizational OP-assigned folder per {@link #itemVariants} entry - see {@code /class whitelist set_folder}. Absent = uncategorized. */
    private static Map<ResourceLocation, String> variantFolders = Map.of();
    private static List<ResourceLocation> protectedItems = List.of();
    private static Map<ResourceLocation, Integer> spawnKit = Map.of();
    private static List<ResourceLocation> hammerBlocks = List.of();
    /** Mirrors {@code LoadoutManager#getBannedItems} - items hard-blocked from equipping regardless of whitelist (see {@code /class ban}). */
    private static List<ResourceLocation> bannedItems = List.of();
    /** Slots an OP has locked on the local player's own loadout (see {@code LoadoutManager#lockSlot}) - never someone else's. */
    private static Set<LoadoutSlot> lockedSlots = Set.of();
    /** Mirrors {@code LoadoutManager#isWhitelistEnabled} - false while an OP has temporarily disabled whitelist enforcement (see {@code /class whitelist enable|disable}). */
    private static boolean whitelistEnabled = true;
    /** OP-curated shop prices (see {@code LoadoutManager#itemPrices}), same for everyone. Insertion order preserved for a stable shop grid. */
    private static Map<ResourceLocation, Integer> prices = Map.of();
    /** The local player's own point balance - see {@code /class points add|set}. */
    private static int points;
    /** Priced items the local player has already bought via {@code /class buy} - never someone else's. */
    private static Set<ResourceLocation> purchasedItems = Set.of();
    /** The local player's own personal presets (see {@code /class mypreset}) - never someone else's, capped server-side at {@code LoadoutManager#MAX_PERSONAL_PRESETS}. */
    private static List<LoadoutSyncPacket.Entry> personalPresets = List.of();
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
                                              List<LoadoutSlot> newLockedSlots,
                                              boolean newWhitelistEnabled,
                                              List<LoadoutSyncPacket.PriceEntry> newPrices,
                                              int newPoints,
                                              List<ResourceLocation> newPurchasedItems,
                                              List<ResourceLocation> newBannedItems,
                                              List<LoadoutSyncPacket.Entry> newPersonalPresets) {
        classes = List.copyOf(newClasses);
        personalPresets = List.copyOf(newPersonalPresets);
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
        Map<ResourceLocation, String> folders = new HashMap<>();
        for (LoadoutSyncPacket.VariantEntry v : newVariants) {
            variants.put(v.id(), v.stack());
            registeredAt.put(v.id(), v.registeredAt());
            if (!v.folder().isBlank()) {
                folders.put(v.id(), v.folder());
            }
        }
        itemVariants = variants;
        variantRegisteredAt = registeredAt;
        variantFolders = folders;
        protectedItems = List.copyOf(newProtectedItems);
        Map<ResourceLocation, Integer> kit = new HashMap<>();
        for (LoadoutSyncPacket.SpawnKitEntry s : newSpawnKit) {
            kit.put(s.item(), s.count());
        }
        spawnKit = kit;
        hammerBlocks = List.copyOf(newHammerBlocks);
        lockedSlots = newLockedSlots.isEmpty() ? Set.of() : EnumSet.copyOf(newLockedSlots);
        whitelistEnabled = newWhitelistEnabled;
        Map<ResourceLocation, Integer> priceMap = new LinkedHashMap<>();
        for (LoadoutSyncPacket.PriceEntry p : newPrices) {
            priceMap.put(p.item(), p.cost());
        }
        prices = priceMap;
        points = newPoints;
        purchasedItems = newPurchasedItems.isEmpty() ? Set.of() : new java.util.HashSet<>(newPurchasedItems);
        bannedItems = List.copyOf(newBannedItems);
        revision++;
    }

    public static synchronized void clear() {
        classes = List.of();
        personal = new LoadoutSyncPacket.PersonalData(null, null, null, null, null, null, null, null, null, null);
        whitelists = EMPTY_WHITELISTS;
        ammoGrants = Map.of();
        itemVariants = Map.of();
        variantRegisteredAt = Map.of();
        variantFolders = Map.of();
        protectedItems = List.of();
        spawnKit = Map.of();
        hammerBlocks = List.of();
        bannedItems = List.of();
        lockedSlots = Set.of();
        whitelistEnabled = true;
        prices = Map.of();
        points = 0;
        purchasedItems = Set.of();
        personalPresets = List.of();
        revision++;
    }

    public static synchronized List<LoadoutSyncPacket.Entry> getClasses() {
        return new ArrayList<>(classes);
    }

    /** The local player's own personal presets (see {@code /class mypreset}) - never someone else's. */
    public static synchronized List<LoadoutSyncPacket.Entry> getPersonalPresets() {
        return new ArrayList<>(personalPresets);
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

    /** The OP-assigned folder for a held-item variant id, or "" if uncategorized. */
    public static synchronized String getVariantFolder(ResourceLocation id) {
        return variantFolders.getOrDefault(id, "");
    }

    /** Every distinct folder name currently in use, alphabetical - for building the whitelist editor's folder tabs. */
    public static synchronized List<String> getVariantFolders() {
        return variantFolders.values().stream().distinct().sorted().toList();
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

    /** True if an OP has temporarily banned this item (see {@code /class ban}) - blocked from equipping regardless of whitelist. */
    public static synchronized boolean isBanned(ResourceLocation item) {
        return bannedItems.contains(item);
    }

    /** True if an OP has locked this slot on the local player's own loadout - see {@code LoadoutManager#lockSlot}. */
    public static synchronized boolean isLocked(LoadoutSlot slot) {
        return lockedSlots.contains(slot);
    }

    /** Mirrors {@code LoadoutManager#isWhitelistEnabled} - see {@code /class whitelist enable|disable}. */
    public static synchronized boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    /** OP-curated shop prices - item -> point cost. Only items with an entry here are "for sale" (see {@code /class price set}). */
    public static synchronized Map<ResourceLocation, Integer> getPrices() {
        return prices;
    }

    /** The local player's own point balance. */
    public static synchronized int getPoints() {
        return points;
    }

    /** True if the local player has already bought this priced item via {@code /class buy}. */
    public static synchronized boolean isPurchased(ResourceLocation item) {
        return purchasedItems.contains(item);
    }

    private LoadoutClientData() {}
}
