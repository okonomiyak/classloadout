package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;
import uk.iwaservice.classloadout.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative registry of loadout classes and each player's current
 * selection, persisted with the overworld. The only writers are the OP-only
 * {@code /class} commands - there is no other mutation path (no config-file
 * editing, no C2S packets).
 */
public class LoadoutManager extends SavedData {
    private static final String DATA_NAME = "classloadout_classes";

    /** Insertion order preserved so the editor/select screens list classes consistently. */
    private final Map<UUID, ClassDefinition> classes = new LinkedHashMap<>();
    private final Map<UUID, UUID> selections = new java.util.HashMap<>();

    public static LoadoutManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(LoadoutManager::load, LoadoutManager::new, DATA_NAME);
    }

    public LoadoutManager() {
    }

    public List<ClassDefinition> list() {
        return new ArrayList<>(classes.values());
    }

    @Nullable
    public ClassDefinition get(UUID id) {
        return classes.get(id);
    }

    public void saveOrUpdate(MinecraftServer server, ClassDefinition definition) {
        classes.put(definition.id(), definition);
        setDirty();
        broadcastAll(server);
    }

    public boolean delete(MinecraftServer server, UUID id) {
        boolean removed = classes.remove(id) != null;
        if (removed) {
            setDirty();
            broadcastAll(server);
        }
        return removed;
    }

    public void select(MinecraftServer server, ServerPlayer player, UUID classId) {
        selections.put(player.getUUID(), classId);
        setDirty();
        sendTo(server, player);
    }

    public void clearSelection(MinecraftServer server, ServerPlayer player) {
        if (selections.remove(player.getUUID()) != null) {
            setDirty();
        }
        sendTo(server, player);
    }

    @Nullable
    public UUID getSelectedId(UUID player) {
        return selections.get(player);
    }

    /** The player's selected class, or null if they have none selected or it was since deleted. */
    @Nullable
    public ClassDefinition getSelectedDefinition(UUID player) {
        UUID id = selections.get(player);
        return id == null ? null : classes.get(id);
    }

    // --- sync ---

    private void broadcastAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(server, player);
        }
    }

    public void sendTo(MinecraftServer server, ServerPlayer player) {
        List<LoadoutSyncPacket.Entry> entries = new ArrayList<>(classes.size());
        for (ClassDefinition def : classes.values()) {
            entries.add(LoadoutSyncPacket.Entry.of(def));
        }
        NetworkHandler.sendLoadoutSync(player, new LoadoutSyncPacket(entries, selections.get(player.getUUID())));
    }

    // --- persistence ---

    public static LoadoutManager load(CompoundTag tag) {
        LoadoutManager manager = new LoadoutManager();
        ListTag classList = tag.getList("Classes", Tag.TAG_COMPOUND);
        for (int i = 0; i < classList.size(); i++) {
            ClassDefinition def = ClassDefinition.load(classList.getCompound(i));
            manager.classes.put(def.id(), def);
        }
        ListTag selectionList = tag.getList("Selections", Tag.TAG_COMPOUND);
        for (int i = 0; i < selectionList.size(); i++) {
            CompoundTag s = selectionList.getCompound(i);
            manager.selections.put(s.getUUID("Player"), s.getUUID("Class"));
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag classList = new ListTag();
        for (ClassDefinition def : classes.values()) {
            classList.add(def.save());
        }
        tag.put("Classes", classList);

        ListTag selectionList = new ListTag();
        for (Map.Entry<UUID, UUID> e : selections.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Player", e.getKey());
            s.putUUID("Class", e.getValue());
            selectionList.add(s);
        }
        tag.put("Selections", selectionList);
        return tag;
    }
}
