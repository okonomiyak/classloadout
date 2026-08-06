package uk.iwaservice.classloadout.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.classloadout.loadout.AmmoGrant;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side mirror of the preset roster, the per-slot whitelists, and the
 * local player's own personal loadout, fed exclusively by
 * {@link LoadoutSyncPacket}. Read by the preset editor (OP), the whitelist
 * editor (OP) and the loadout screen (everyone).
 */
public final class LoadoutClientData {

    private static final LoadoutSyncPacket.Whitelists EMPTY_WHITELISTS =
            new LoadoutSyncPacket.Whitelists(List.of(), List.of(), List.of(), List.of(), List.of());

    private static List<LoadoutSyncPacket.Entry> classes = List.of();
    private static LoadoutSyncPacket.PersonalData personal =
            new LoadoutSyncPacket.PersonalData(null, null, null, null, null);
    private static LoadoutSyncPacket.Whitelists whitelists = EMPTY_WHITELISTS;
    private static Map<LoadoutSlot, Map<ResourceLocation, AmmoGrant>> ammoGrants = Map.of();
    private static Map<ResourceLocation, CompoundTag> itemVariants = Map.of();
    private static List<ResourceLocation> protectedItems = List.of();
    private static Map<ResourceLocation, Integer> spawnKit = Map.of();
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
                                              List<LoadoutSyncPacket.SpawnKitEntry> newSpawnKit) {
        classes = List.copyOf(newClasses);
        personal = newPersonal;
        whitelists = newWhitelists;
        Map<LoadoutSlot, Map<ResourceLocation, AmmoGrant>> grants = new EnumMap<>(LoadoutSlot.class);
        for (LoadoutSyncPacket.AmmoGrantEntry g : newAmmoGrants) {
            grants.computeIfAbsent(g.slot(), s -> new HashMap<>()).put(g.item(), new AmmoGrant(g.ammoItem(), g.count()));
        }
        ammoGrants = grants;
        Map<ResourceLocation, CompoundTag> variants = new HashMap<>();
        for (LoadoutSyncPacket.VariantEntry v : newVariants) {
            variants.put(v.id(), v.stack());
        }
        itemVariants = variants;
        protectedItems = List.copyOf(newProtectedItems);
        Map<ResourceLocation, Integer> kit = new HashMap<>();
        for (LoadoutSyncPacket.SpawnKitEntry s : newSpawnKit) {
            kit.put(s.item(), s.count());
        }
        spawnKit = kit;
        revision++;
    }

    public static synchronized void clear() {
        classes = List.of();
        personal = new LoadoutSyncPacket.PersonalData(null, null, null, null, null);
        whitelists = EMPTY_WHITELISTS;
        ammoGrants = Map.of();
        itemVariants = Map.of();
        protectedItems = List.of();
        spawnKit = Map.of();
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

    @Nullable
    public static synchronized AmmoGrant getAmmoGrant(LoadoutSlot slot, ResourceLocation item) {
        Map<ResourceLocation, AmmoGrant> bySlot = ammoGrants.get(slot);
        return bySlot == null ? null : bySlot.get(item);
    }

    /** Client-side mirror of {@code LoadoutManager.getItemVariants()}; used to resolve slot/whitelist ids back into real ItemStacks. */
    public static synchronized Map<ResourceLocation, CompoundTag> getItemVariants() {
        return itemVariants;
    }

    public static synchronized List<ResourceLocation> getProtectedItems() {
        return protectedItems;
    }

    public static synchronized Map<ResourceLocation, Integer> getSpawnKit() {
        return spawnKit;
    }

    private LoadoutClientData() {}
}
