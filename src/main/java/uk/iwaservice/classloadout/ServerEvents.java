package uk.iwaservice.classloadout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import uk.iwaservice.classloadout.command.ClassCommand;
import uk.iwaservice.classloadout.loadout.AmmoGrant;
import uk.iwaservice.classloadout.loadout.LoadoutManager;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.loadout.PersonalLoadout;

import java.util.ArrayList;
import java.util.List;

/** Forge-bus event handlers: command registration, respawn equip, login sync, hammer AOE breaking. */
public final class ServerEvents {

    /**
     * The generic data-pack-driven "hammer" item tag - matches every SuperbWarfare hammer
     * variant (and anything else that opts in) without a compile-time dependency on
     * SuperbWarfare itself, unlike the rest of this mod's TACZ/SuperbWarfare integrations
     * under {@code compat/}. If SuperbWarfare isn't installed, the tag is simply empty and
     * this check always fails harmlessly.
     */
    private static final TagKey<Item> HAMMER_TAG = ItemTags.create(new ResourceLocation("forge", "tools/hammer"));

    /** How far a guard spawner looks for its own tagged entity before assuming it's gone and starting the respawn countdown. */
    private static final double GUARD_SPAWNER_SCAN_RADIUS = 48.0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ClassCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoadoutManager.get(player.server).sendTo(player.server, player);
        }
    }

    /** Throttled to once a second - a per-tick full scan of every guard spawner isn't worth the precision. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        tickGuardSpawners(server);
    }

    /**
     * For each configured guard spawner: if its tagged entity is present nearby, clear the
     * missing-timer; if it's been missing for at least the configured delay, respawn it on top
     * of the block. A fresh server start simply re-observes each spawner's state on the next
     * tick - the missing-timer itself isn't persisted, unlike the config.
     */
    private static void tickGuardSpawners(MinecraftServer server) {
        LoadoutManager manager = LoadoutManager.get(server);
        for (GlobalPos gpos : List.copyOf(manager.getGuardSpawnerPositions())) {
            ResourceLocation entityTypeId = manager.getGuardSpawnerEntity(gpos);
            ServerLevel level = server.getLevel(gpos.dimension());
            if (entityTypeId == null || level == null || !level.isLoaded(gpos.pos())) {
                continue;
            }
            String tag = guardSpawnerTag(gpos.pos());
            boolean present = !level.getEntities((Entity) null, new AABB(gpos.pos()).inflate(GUARD_SPAWNER_SCAN_RADIUS),
                    e -> e.getTags().contains(tag)).isEmpty();
            if (present) {
                manager.setGuardSpawnerMissingSince(gpos, -1);
                continue;
            }
            long missingSince = manager.getGuardSpawnerMissingSince(gpos);
            if (missingSince < 0) {
                manager.setGuardSpawnerMissingSince(gpos, server.getTickCount());
                continue;
            }
            if (server.getTickCount() - missingSince >= manager.getGuardSpawnerDelaySeconds(gpos) * 20L) {
                spawnGuard(level, gpos.pos(), entityTypeId, tag, manager.getGuardSpawnerItems(gpos), manager);
                manager.setGuardSpawnerMissingSince(gpos, -1);
            }
        }
    }

    private static String guardSpawnerTag(BlockPos pos) {
        return "classloadout_guard_" + pos.asLong();
    }

    /** Spawns on top of the block, tags it so the next tick recognizes it as this spawner's guard, and fills any item-handler capability it exposes (e.g. a SuperbWarfare vehicle's battery/ammo slots) with the configured items. */
    private static void spawnGuard(ServerLevel level, BlockPos pos, ResourceLocation entityTypeId, String tag,
            List<ResourceLocation> items, LoadoutManager manager) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
        if (type == null) {
            return;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            return;
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        entity.addTag(tag);
        entity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            int slot = 0;
            for (ResourceLocation itemId : items) {
                if (slot >= handler.getSlots()) {
                    break;
                }
                ItemStack stack = ItemResolver.resolve(itemId, manager.getItemVariants());
                if (stack != null && !stack.isEmpty()) {
                    handler.insertItem(slot, stack.copy(), false);
                }
                slot++;
            }
        });
        level.addFreshEntity(entity);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                "classloadout.msg.guardspawner_respawned", entityTypeId.toString(), pos.getX(), pos.getY(), pos.getZ()),
                false);
    }

    /**
     * Breaking a block on the OP-curated hammer-blocks whitelist (see
     * {@code /class hammerblocks}) with a hammer (anything in {@link #HAMMER_TAG}) also
     * destroys every other whitelisted block within {@code hammerAoeRadius} - an
     * explosion-like burst, but limited to block types an OP has explicitly approved so it
     * can't be used to grief arbitrary terrain/builds. The originally-broken block must
     * itself be whitelisted, or no bonus blocks break at all (a hammer is still just a
     * normal tool against everything else). {@link net.minecraft.world.level.Level#destroyBlock}
     * doesn't re-fire this event, so there's no risk of a recursive chain reaction.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        int radius = Config.HAMMER_AOE_RADIUS.get();
        if (radius <= 0 || !(player instanceof ServerPlayer) || !(event.getLevel() instanceof ServerLevel serverLevel)
                || !player.getMainHandItem().is(HAMMER_TAG)) {
            return;
        }
        LoadoutManager manager = LoadoutManager.get(serverLevel.getServer());
        ResourceLocation centerBlockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (centerBlockId == null || !manager.isHammerBlock(centerBlockId)) {
            return;
        }
        BlockPos center = event.getPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (pos.equals(center) || !serverLevel.isLoaded(pos)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (blockId != null && manager.isHammerBlock(blockId)) {
                serverLevel.destroyBlock(pos, true, player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LoadoutManager manager = LoadoutManager.get(player.server);
        // Matches equip-on-respawn's own convention: a player who has never touched their
        // loadout is left alone entirely, by both the clear and the equip below. The spawn
        // kit is deliberately outside this gate - it's not part of the loadout system at
        // all, so it applies to every respawning player unconditionally.
        if (manager.getPersonalLoadout(player.getUUID()) != null) {
            if (Config.CLEAR_INVENTORY_ON_DEATH.get()) {
                clearInventoryExceptProtected(player, manager);
            }
            grantAmmoForSlots(player, equipLoadout(player, manager), manager);
        }
        grantSpawnKit(player, manager);
    }

    /** Shared by respawn and the immediate-equip path below - grants each slot's configured ammo, if any. */
    private static void grantAmmoForSlots(ServerPlayer player, ResourceLocation[] slots, LoadoutManager manager) {
        LoadoutSlot[] slotKeys = LoadoutSlot.values();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                AmmoGrant grant = manager.getAmmoGrant(slotKeys[i], slots[i]);
                if (grant != null) {
                    grantAmmo(player, grant, manager);
                }
            }
        }
    }

    /** Gives every OP-curated spawn kit entry to the player, unconditionally, on every respawn. */
    private static void grantSpawnKit(ServerPlayer player, LoadoutManager manager) {
        for (var entry : manager.getSpawnKit().entrySet()) {
            giveItem(player, entry.getKey(), entry.getValue(), manager);
        }
    }

    /**
     * Wipes the player's entire inventory (main inventory, armor, offhand) except items
     * whose base type is on the OP-curated protected-items list, before the loadout is
     * re-equipped over it. Meant to be paired with the {@code keepInventory} gamerule
     * turned on - if it's off, vanilla already dropped (and permanently lost) everything,
     * protected or not, before this event ever fires.
     */
    private static void clearInventoryExceptProtected(ServerPlayer player, LoadoutManager manager) {
        Inventory inventory = player.getInventory();
        clearExceptProtected(inventory.items, manager);
        clearExceptProtected(inventory.armor, manager);
        clearExceptProtected(inventory.offhand, manager);
    }

    private static void clearExceptProtected(NonNullList<ItemStack> slots, LoadoutManager manager) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null || !manager.isProtectedItem(itemId)) {
                slots.set(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Overwrites hotbar slots 0-4 with the player's own loadout's
     * main/sidearm/throwable/gadget/melee items (in that fixed order),
     * clearing any slot they've left unset. Overwriting rather than adding
     * keeps the icon-row order deterministic and avoids duplicate gear if
     * the keepInventory gamerule is on. A player who has never touched
     * their loadout (no assign/select/clear yet) is left alone entirely
     * (returns null).
     *
     * <p>Called on respawn (see below), but also directly from {@code
     * ClassCommand}'s assign/select/clear handlers so a player's gear takes
     * effect immediately while alive (e.g. via the loadout station), not
     * only the next time they die. Doesn't grant ammo itself - callers pass
     * the returned slot array into {@link #grantAmmoForSlots} for that.
     */
    public static ResourceLocation[] equipLoadout(ServerPlayer player, LoadoutManager manager) {
        PersonalLoadout loadout = manager.getPersonalLoadout(player.getUUID());
        if (loadout == null) {
            return null;
        }
        ResourceLocation[] slots = {loadout.main(), loadout.sidearm(), loadout.throwable(), loadout.gadget(), loadout.melee()};
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i] == null ? null : ItemResolver.resolve(slots[i], manager.getItemVariants());
            player.getInventory().setItem(i, stack == null ? ItemStack.EMPTY : stack);
        }
        return slots;
    }

    /**
     * Equips immediately (see {@link #equipLoadout(ServerPlayer, LoadoutManager)}) and, per-slot,
     * grants that slot's configured ammo too - same as respawn. Also runs the same
     * clear-except-protected step as respawn first, when {@code death.clearInventoryOnDeath} is
     * on, so re-visiting the loadout station/locker while alive behaves exactly like a respawn
     * instead of just swapping the five loadout slots over whatever else is in the inventory.
     */
    public static void equipLoadout(ServerPlayer player) {
        LoadoutManager manager = LoadoutManager.get(player.server);
        if (Config.CLEAR_INVENTORY_ON_DEATH.get()) {
            clearInventoryExceptProtected(player, manager);
        }
        ResourceLocation[] slots = equipLoadout(player, manager);
        if (slots != null) {
            grantAmmoForSlots(player, slots, manager);
        }
    }

    private static void grantAmmo(ServerPlayer player, AmmoGrant grant, LoadoutManager manager) {
        giveItem(player, grant.ammoItem(), grant.count(), manager);
    }

    /**
     * Gives {@code count} of {@code itemId} into the player's general inventory (not
     * overwriting anything, dropping any overflow that doesn't fit). A single-instance item
     * (max stack size 1 - e.g. a registered TACZ ammo box variant, which carries its own
     * internal ammo count in its tag) can't be usefully split into {@code count} separate
     * copies, so that case just gives the one exact item as registered and ignores the count.
     * Shared by ammo grants and the spawn kit - both are "resolve an id, give N of it" grants,
     * just triggered differently (per-slot on equip vs. unconditionally on every respawn).
     */
    private static void giveItem(ServerPlayer player, ResourceLocation itemId, int count, LoadoutManager manager) {
        ItemStack template = ItemResolver.resolve(itemId, manager.getItemVariants());
        if (template == null || template.isEmpty()) {
            return;
        }
        int maxStack = template.getMaxStackSize();
        if (maxStack <= 1) {
            giveOrDrop(player, template.copy());
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = template.copyWithCount(Math.min(maxStack, remaining));
            remaining -= stack.getCount();
            giveOrDrop(player, stack);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        boolean added = player.getInventory().add(stack);
        if (!added || !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private ServerEvents() {}
}
