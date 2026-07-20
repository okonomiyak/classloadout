package uk.iwaservice.classloadout.client;

import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side mirror of the loadout-class roster and the local player's
 * selection, fed exclusively by {@link LoadoutSyncPacket}. Read by both the
 * class editor (OP) and the class-select screen (everyone).
 */
public final class LoadoutClientData {

    private static List<LoadoutSyncPacket.Entry> classes = List.of();
    @Nullable
    private static UUID selectedId;
    /** Incremented on every sync; lets screens detect updates cheaply. */
    private static int revision;

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized void applySync(List<LoadoutSyncPacket.Entry> newClasses, @Nullable UUID newSelectedId) {
        classes = List.copyOf(newClasses);
        selectedId = newSelectedId;
        revision++;
    }

    public static synchronized void clear() {
        classes = List.of();
        selectedId = null;
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

    @Nullable
    public static synchronized UUID getSelectedId() {
        return selectedId;
    }

    /** True if the given item is known to the client's item registry (used for the "not installed" grey-out). */
    public static boolean isItemAvailable(@Nullable ResourceLocation item) {
        return item == null || net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(item);
    }

    private LoadoutClientData() {}
}
