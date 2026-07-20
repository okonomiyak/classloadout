package uk.iwaservice.classloadout.client;

import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
    /** Incremented on every sync; lets screens detect updates cheaply. */
    private static int revision;

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized void applySync(List<LoadoutSyncPacket.Entry> newClasses,
                                              LoadoutSyncPacket.PersonalData newPersonal,
                                              LoadoutSyncPacket.Whitelists newWhitelists) {
        classes = List.copyOf(newClasses);
        personal = newPersonal;
        whitelists = newWhitelists;
        revision++;
    }

    public static synchronized void clear() {
        classes = List.of();
        personal = new LoadoutSyncPacket.PersonalData(null, null, null, null, null);
        whitelists = EMPTY_WHITELISTS;
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

    /** True if the given item is known to the client's item registry (used for the "not installed" grey-out). */
    public static boolean isItemAvailable(@Nullable ResourceLocation item) {
        return item == null || net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(item);
    }

    private LoadoutClientData() {}
}
