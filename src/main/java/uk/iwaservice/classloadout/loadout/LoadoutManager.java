package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
 * Server-authoritative registry of two things, persisted with the overworld:
 * admin-defined preset classes, and each player's own personal loadout (the
 * thing that actually gets equipped on respawn). The only writers are the
 * {@code /class} commands - there is no other mutation path (no config-file
 * editing, no C2S packets). Presets are OP-only; a player's own loadout is
 * self-service (assigning a slot, or applying a preset as a starting point).
 */
public class LoadoutManager extends SavedData {
    private static final String DATA_NAME = "classloadout_classes";

    /** Insertion order preserved so the editor/select screens list classes consistently. */
    private final Map<UUID, ClassDefinition> classes = new LinkedHashMap<>();
    /** Absent entry = player has never touched their loadout; present (even if all-empty) = they have. */
    private final Map<UUID, PersonalLoadout> personalLoadouts = new java.util.HashMap<>();

    public static LoadoutManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(LoadoutManager::load, LoadoutManager::new, DATA_NAME);
    }

    public LoadoutManager() {
    }

    // --- presets (admin-managed) ---

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

    // --- personal loadout (player self-service) ---

    /** Null means the player has never touched their loadout - equip-on-respawn leaves their inventory alone. */
    @Nullable
    public PersonalLoadout getPersonalLoadout(UUID player) {
        return personalLoadouts.get(player);
    }

    /** Sets a single slot in the player's own loadout; a null item clears that slot. */
    public void setSlot(MinecraftServer server, ServerPlayer player, LoadoutSlot slot, @Nullable ResourceLocation item) {
        PersonalLoadout current = personalLoadouts.getOrDefault(player.getUUID(), PersonalLoadout.EMPTY);
        personalLoadouts.put(player.getUUID(), current.withSlot(slot, item));
        setDirty();
        sendTo(server, player);
    }

    /** Copies a preset's five items into the player's own loadout as a starting point. Returns false if the preset doesn't exist. */
    public boolean applyPreset(MinecraftServer server, ServerPlayer player, UUID classId) {
        ClassDefinition def = classes.get(classId);
        if (def == null) {
            return false;
        }
        personalLoadouts.put(player.getUUID(), PersonalLoadout.fromClass(def));
        setDirty();
        sendTo(server, player);
        return true;
    }

    /** Resets the player back to "never touched their loadout" (equip-on-respawn stops overwriting their hotbar). */
    public void clearPersonalLoadout(MinecraftServer server, ServerPlayer player) {
        if (personalLoadouts.remove(player.getUUID()) != null) {
            setDirty();
        }
        sendTo(server, player);
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
        PersonalLoadout personal = personalLoadouts.getOrDefault(player.getUUID(), PersonalLoadout.EMPTY);
        NetworkHandler.sendLoadoutSync(player, new LoadoutSyncPacket(entries, LoadoutSyncPacket.PersonalData.of(personal)));
    }

    // --- persistence ---

    public static LoadoutManager load(CompoundTag tag) {
        LoadoutManager manager = new LoadoutManager();
        ListTag classList = tag.getList("Classes", Tag.TAG_COMPOUND);
        for (int i = 0; i < classList.size(); i++) {
            ClassDefinition def = ClassDefinition.load(classList.getCompound(i));
            manager.classes.put(def.id(), def);
        }
        ListTag personalList = tag.getList("PersonalLoadouts", Tag.TAG_COMPOUND);
        for (int i = 0; i < personalList.size(); i++) {
            CompoundTag p = personalList.getCompound(i);
            manager.personalLoadouts.put(p.getUUID("Player"), PersonalLoadout.load(p.getCompound("Loadout")));
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

        ListTag personalList = new ListTag();
        for (Map.Entry<UUID, PersonalLoadout> e : personalLoadouts.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Player", e.getKey());
            p.put("Loadout", e.getValue().save());
            personalList.add(p);
        }
        tag.put("PersonalLoadouts", personalList);
        return tag;
    }
}
