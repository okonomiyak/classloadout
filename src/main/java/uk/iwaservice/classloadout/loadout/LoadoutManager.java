package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import uk.iwaservice.classloadout.ClassLoadoutMod;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;
import uk.iwaservice.classloadout.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative registry of six things, persisted with the
 * overworld: admin-defined preset classes, the OP-curated per-slot item
 * whitelists, the OP-curated protected-items list (exempt from the on-death
 * inventory clear, see {@code Config.CLEAR_INVENTORY_ON_DEATH}), the
 * OP-curated spawn kit (item/count pairs given to every player on every
 * respawn, unconditionally - not tied to any loadout slot or whitelist),
 * the OP-curated hammer AOE block whitelist (block types a SuperbWarfare
 * hammer's area-of-effect break is allowed to also destroy), and each
 * player's own personal loadout (the thing that actually gets equipped on
 * respawn). The only writers are the {@code /class} commands - there is no
 * other mutation path (no config-file editing, no C2S packets). Presets,
 * whitelists, protected items, the spawn kit and the hammer block list are
 * OP-only; a player's own loadout is self-service (assigning a whitelisted
 * item to a slot, or applying a preset as a starting point - presets are
 * not themselves whitelist-restricted, since defining one already requires
 * OP permission).
 */
public class LoadoutManager extends SavedData {
    private static final String DATA_NAME = "classloadout_classes";

    /** Insertion order preserved so the editor/select screens list classes consistently. */
    private final Map<UUID, ClassDefinition> classes = new LinkedHashMap<>();
    /** Absent entry = player has never touched their loadout; present (even if all-empty) = they have. */
    private final Map<UUID, PersonalLoadout> personalLoadouts = new java.util.HashMap<>();
    /** Insertion order preserved for a stable whitelist-editor grid; empty (or absent) = nothing assignable yet. */
    private final Map<LoadoutSlot, Set<ResourceLocation>> whitelists = new EnumMap<>(LoadoutSlot.class);
    /** Optional per-whitelist-entry ammo grant (see {@link AmmoGrant}); absent = that item grants no ammo. */
    private final Map<LoadoutSlot, Map<ResourceLocation, AmmoGrant>> ammoGrants = new EnumMap<>(LoadoutSlot.class);
    /**
     * OP-registered "exact held item" whitelist entries: a synthetic
     * {@code classloadout:variant_<uuid>} id mapped to the full saved NBT of
     * whatever item was in the OP's hand when they registered it (item,
     * count and tag - enchantments, custom name, TACZ gun attachments,
     * anything). These ids flow through {@link #whitelists}/{@link PersonalLoadout}
     * exactly like any other item id; only resolving one back into a real
     * {@link ItemStack} (see {@link uk.iwaservice.classloadout.ItemResolver})
     * needs to know about this map.
     */
    private final Map<ResourceLocation, CompoundTag> itemVariants = new LinkedHashMap<>();
    /** OP-curated: items that survive the on-death inventory clear (see {@code clearInventoryOnDeath}). Matched by base item type, not exact NBT. */
    private final Set<ResourceLocation> protectedItems = new LinkedHashSet<>();
    /** OP-curated: item -> count given to every player on every respawn, unconditionally (not tied to the loadout system at all - see {@code ServerEvents}). Insertion order preserved for a stable editor grid. */
    private final Map<ResourceLocation, Integer> spawnKit = new LinkedHashMap<>();
    /** OP-curated: block types (registry names, not items) a SuperbWarfare hammer's area-of-effect break can also destroy - see {@code Config.HAMMER_AOE_RADIUS}. */
    private final Set<ResourceLocation> hammerBlocks = new LinkedHashSet<>();

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

    // --- slot whitelists (OP-curated) ---

    public Set<ResourceLocation> getWhitelist(LoadoutSlot slot) {
        return whitelists.getOrDefault(slot, Set.of());
    }

    public boolean isWhitelisted(LoadoutSlot slot, ResourceLocation item) {
        return getWhitelist(slot).contains(item);
    }

    public void addToWhitelist(MinecraftServer server, LoadoutSlot slot, ResourceLocation item) {
        if (whitelists.computeIfAbsent(slot, s -> new LinkedHashSet<>()).add(item)) {
            setDirty();
            broadcastAll(server);
        }
    }

    public void removeFromWhitelist(MinecraftServer server, LoadoutSlot slot, ResourceLocation item) {
        Set<ResourceLocation> set = whitelists.get(slot);
        boolean changed = set != null && set.remove(item);
        Map<ResourceLocation, AmmoGrant> grants = ammoGrants.get(slot);
        if (grants != null && grants.remove(item) != null) {
            changed = true;
        }
        if (changed) {
            setDirty();
            broadcastAll(server);
        }
        // Deliberately not garbage-collecting itemVariants here: the same variant id could still
        // be whitelisted on another slot, and an orphaned entry is harmless (just a few bytes of NBT).
    }

    /**
     * Registers {@code held} (item, count and full tag preserved as-is) as a new whitelist
     * entry for {@code slot} under a fresh synthetic id, so an OP can whitelist an exact
     * NBT-bearing item (enchantments, custom name, TACZ gun attachments, ...) rather than
     * just the bare item type. Returns null (no-op) if {@code held} is empty.
     */
    @Nullable
    public ResourceLocation addHeldItemToWhitelist(MinecraftServer server, LoadoutSlot slot, ItemStack held) {
        if (held.isEmpty()) {
            return null;
        }
        ResourceLocation variantId = variantId(UUID.randomUUID());
        itemVariants.put(variantId, held.save(new CompoundTag()));
        whitelists.computeIfAbsent(slot, s -> new LinkedHashSet<>()).add(variantId);
        setDirty();
        broadcastAll(server);
        return variantId;
    }

    /**
     * Same idea as {@link #addHeldItemToWhitelist}, but registers the variant without adding
     * it to any slot's whitelist - used by the ammo grant popup, which just needs a specific
     * NBT-bearing item id to store as an {@link AmmoGrant#ammoItem()}, not a whitelist entry.
     * {@code id} is client-generated (not server-generated like the whitelist flow above)
     * because the ammo grant screen needs to know the resulting id immediately, before the
     * next sync round-trip, to fill in its own pending state.
     */
    public void registerItemVariant(MinecraftServer server, UUID id, ItemStack held) {
        if (held.isEmpty()) {
            return;
        }
        itemVariants.put(variantId(id), held.save(new CompoundTag()));
        setDirty();
        broadcastAll(server);
    }

    private static ResourceLocation variantId(UUID id) {
        return new ResourceLocation(ClassLoadoutMod.MODID, "variant_" + id);
    }

    /** Server-side counterpart of {@code LoadoutClientData.getItemVariants()}; used to resolve slot/whitelist ids back into real ItemStacks (see {@link uk.iwaservice.classloadout.ItemResolver}). */
    public Map<ResourceLocation, CompoundTag> getItemVariants() {
        return itemVariants;
    }

    // --- per-whitelist-entry ammo grants (OP-curated) ---

    @Nullable
    public AmmoGrant getAmmoGrant(LoadoutSlot slot, ResourceLocation item) {
        Map<ResourceLocation, AmmoGrant> grants = ammoGrants.get(slot);
        return grants == null ? null : grants.get(item);
    }

    /** A count of 0 (or less) clears the grant instead of setting one. */
    public void setAmmoGrant(MinecraftServer server, LoadoutSlot slot, ResourceLocation item, ResourceLocation ammoItem, int count) {
        if (count <= 0) {
            clearAmmoGrant(server, slot, item);
            return;
        }
        ammoGrants.computeIfAbsent(slot, s -> new LinkedHashMap<>()).put(item, new AmmoGrant(ammoItem, count));
        setDirty();
        broadcastAll(server);
    }

    public void clearAmmoGrant(MinecraftServer server, LoadoutSlot slot, ResourceLocation item) {
        Map<ResourceLocation, AmmoGrant> grants = ammoGrants.get(slot);
        if (grants != null && grants.remove(item) != null) {
            setDirty();
            broadcastAll(server);
        }
    }

    // --- protected items (OP-curated, exempt from the on-death inventory clear) ---

    public Set<ResourceLocation> getProtectedItems() {
        return protectedItems;
    }

    public boolean isProtectedItem(ResourceLocation item) {
        return protectedItems.contains(item);
    }

    public void addProtectedItem(MinecraftServer server, ResourceLocation item) {
        if (protectedItems.add(item)) {
            setDirty();
            broadcastAll(server);
        }
    }

    public void removeProtectedItem(MinecraftServer server, ResourceLocation item) {
        if (protectedItems.remove(item)) {
            setDirty();
            broadcastAll(server);
        }
    }

    // --- spawn kit (OP-curated, granted to every player on every respawn, unconditionally) ---

    public Map<ResourceLocation, Integer> getSpawnKit() {
        return spawnKit;
    }

    /** A count of 0 (or less) removes the entry instead of setting one. */
    public void setSpawnKitEntry(MinecraftServer server, ResourceLocation item, int count) {
        if (count <= 0) {
            removeSpawnKitEntry(server, item);
            return;
        }
        spawnKit.put(item, count);
        setDirty();
        broadcastAll(server);
    }

    public void removeSpawnKitEntry(MinecraftServer server, ResourceLocation item) {
        if (spawnKit.remove(item) != null) {
            setDirty();
            broadcastAll(server);
        }
    }

    // --- hammer AOE block whitelist (OP-curated) ---

    public Set<ResourceLocation> getHammerBlocks() {
        return hammerBlocks;
    }

    public boolean isHammerBlock(ResourceLocation block) {
        return hammerBlocks.contains(block);
    }

    public void addHammerBlock(MinecraftServer server, ResourceLocation block) {
        if (hammerBlocks.add(block)) {
            setDirty();
            broadcastAll(server);
        }
    }

    public void removeHammerBlock(MinecraftServer server, ResourceLocation block) {
        if (hammerBlocks.remove(block)) {
            setDirty();
            broadcastAll(server);
        }
    }

    // --- personal loadout (player self-service) ---

    /** Null means the player has never touched their loadout - equip-on-respawn leaves their inventory alone. */
    @Nullable
    public PersonalLoadout getPersonalLoadout(UUID player) {
        return personalLoadouts.get(player);
    }

    /**
     * Sets a single slot in the player's own loadout; a null item clears
     * that slot. The caller (the {@code /class assign} command) is
     * responsible for checking {@link #isWhitelisted} before calling this -
     * this method itself doesn't re-validate, so it stays usable for a
     * possible future OP override path.
     */
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

        Map<LoadoutSlot, List<ResourceLocation>> whitelistsBySlot = new EnumMap<>(LoadoutSlot.class);
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            whitelistsBySlot.put(slot, new ArrayList<>(getWhitelist(slot)));
        }

        List<LoadoutSyncPacket.AmmoGrantEntry> ammoGrantEntries = new ArrayList<>();
        for (Map.Entry<LoadoutSlot, Map<ResourceLocation, AmmoGrant>> bySlot : ammoGrants.entrySet()) {
            for (Map.Entry<ResourceLocation, AmmoGrant> e : bySlot.getValue().entrySet()) {
                ammoGrantEntries.add(new LoadoutSyncPacket.AmmoGrantEntry(
                        bySlot.getKey(), e.getKey(), e.getValue().ammoItem(), e.getValue().count()));
            }
        }

        List<LoadoutSyncPacket.VariantEntry> variantEntries = new ArrayList<>(itemVariants.size());
        for (Map.Entry<ResourceLocation, CompoundTag> e : itemVariants.entrySet()) {
            variantEntries.add(new LoadoutSyncPacket.VariantEntry(e.getKey(), e.getValue()));
        }

        List<LoadoutSyncPacket.SpawnKitEntry> spawnKitEntries = new ArrayList<>(spawnKit.size());
        for (Map.Entry<ResourceLocation, Integer> e : spawnKit.entrySet()) {
            spawnKitEntries.add(new LoadoutSyncPacket.SpawnKitEntry(e.getKey(), e.getValue()));
        }

        NetworkHandler.sendLoadoutSync(player, new LoadoutSyncPacket(entries,
                LoadoutSyncPacket.PersonalData.of(personal), LoadoutSyncPacket.Whitelists.of(whitelistsBySlot),
                ammoGrantEntries, variantEntries, new ArrayList<>(protectedItems), spawnKitEntries,
                new ArrayList<>(hammerBlocks)));
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
        ListTag whitelistList = tag.getList("Whitelists", Tag.TAG_COMPOUND);
        for (int i = 0; i < whitelistList.size(); i++) {
            CompoundTag w = whitelistList.getCompound(i);
            LoadoutSlot slot = LoadoutSlot.byKey(w.getString("Slot"));
            if (slot == null) {
                continue;
            }
            Set<ResourceLocation> items = new LinkedHashSet<>();
            ListTag itemList = w.getList("Items", Tag.TAG_STRING);
            for (Tag t : itemList) {
                items.add(new ResourceLocation(t.getAsString()));
            }
            manager.whitelists.put(slot, items);
        }
        ListTag ammoGrantList = tag.getList("AmmoGrants", Tag.TAG_COMPOUND);
        for (int i = 0; i < ammoGrantList.size(); i++) {
            CompoundTag g = ammoGrantList.getCompound(i);
            LoadoutSlot slot = LoadoutSlot.byKey(g.getString("Slot"));
            if (slot == null) {
                continue;
            }
            ResourceLocation item = new ResourceLocation(g.getString("Item"));
            ResourceLocation ammoItem = new ResourceLocation(g.getString("AmmoItem"));
            int count = g.getInt("Count");
            manager.ammoGrants.computeIfAbsent(slot, s -> new LinkedHashMap<>()).put(item, new AmmoGrant(ammoItem, count));
        }
        ListTag variantList = tag.getList("ItemVariants", Tag.TAG_COMPOUND);
        for (int i = 0; i < variantList.size(); i++) {
            CompoundTag v = variantList.getCompound(i);
            manager.itemVariants.put(new ResourceLocation(v.getString("Id")), v.getCompound("Stack"));
        }
        ListTag protectedList = tag.getList("ProtectedItems", Tag.TAG_STRING);
        for (Tag t : protectedList) {
            manager.protectedItems.add(new ResourceLocation(t.getAsString()));
        }
        ListTag spawnKitList = tag.getList("SpawnKit", Tag.TAG_COMPOUND);
        for (int i = 0; i < spawnKitList.size(); i++) {
            CompoundTag s = spawnKitList.getCompound(i);
            manager.spawnKit.put(new ResourceLocation(s.getString("Item")), s.getInt("Count"));
        }
        ListTag hammerBlockList = tag.getList("HammerBlocks", Tag.TAG_STRING);
        for (Tag t : hammerBlockList) {
            manager.hammerBlocks.add(new ResourceLocation(t.getAsString()));
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

        ListTag whitelistList = new ListTag();
        for (Map.Entry<LoadoutSlot, Set<ResourceLocation>> e : whitelists.entrySet()) {
            CompoundTag w = new CompoundTag();
            w.putString("Slot", e.getKey().key());
            ListTag itemList = new ListTag();
            for (ResourceLocation loc : e.getValue()) {
                itemList.add(net.minecraft.nbt.StringTag.valueOf(loc.toString()));
            }
            w.put("Items", itemList);
            whitelistList.add(w);
        }
        tag.put("Whitelists", whitelistList);

        ListTag ammoGrantList = new ListTag();
        for (Map.Entry<LoadoutSlot, Map<ResourceLocation, AmmoGrant>> bySlot : ammoGrants.entrySet()) {
            for (Map.Entry<ResourceLocation, AmmoGrant> e : bySlot.getValue().entrySet()) {
                CompoundTag g = new CompoundTag();
                g.putString("Slot", bySlot.getKey().key());
                g.putString("Item", e.getKey().toString());
                g.putString("AmmoItem", e.getValue().ammoItem().toString());
                g.putInt("Count", e.getValue().count());
                ammoGrantList.add(g);
            }
        }
        tag.put("AmmoGrants", ammoGrantList);

        ListTag variantList = new ListTag();
        for (Map.Entry<ResourceLocation, CompoundTag> e : itemVariants.entrySet()) {
            CompoundTag v = new CompoundTag();
            v.putString("Id", e.getKey().toString());
            v.put("Stack", e.getValue());
            variantList.add(v);
        }
        tag.put("ItemVariants", variantList);

        ListTag protectedList = new ListTag();
        for (ResourceLocation loc : protectedItems) {
            protectedList.add(net.minecraft.nbt.StringTag.valueOf(loc.toString()));
        }
        tag.put("ProtectedItems", protectedList);

        ListTag spawnKitList = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> e : spawnKit.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putString("Item", e.getKey().toString());
            s.putInt("Count", e.getValue());
            spawnKitList.add(s);
        }
        tag.put("SpawnKit", spawnKitList);

        ListTag hammerBlockList = new ListTag();
        for (ResourceLocation loc : hammerBlocks) {
            hammerBlockList.add(net.minecraft.nbt.StringTag.valueOf(loc.toString()));
        }
        tag.put("HammerBlocks", hammerBlockList);
        return tag;
    }
}
