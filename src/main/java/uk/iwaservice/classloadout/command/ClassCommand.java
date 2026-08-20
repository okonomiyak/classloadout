package uk.iwaservice.classloadout.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import uk.iwaservice.classloadout.ServerEvents;
import uk.iwaservice.classloadout.loadout.ClassDefinition;
import uk.iwaservice.classloadout.loadout.LoadoutManager;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.loadout.PersonalLoadout;
import uk.iwaservice.classloadout.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /class} command tree. All loadout operations enter the server
 * exclusively through here (no C2S packets), so the permission checks below
 * are the single line of defense.
 *
 * <p>Three independent things live under here: OP-managed <b>presets</b>
 * (editor/save/delete), OP-curated per-slot <b>whitelists</b> (whitelist
 * .../add/remove), and each player's own self-service <b>personal
 * loadout</b> (assign/select/clear) - the personal loadout, not any preset,
 * is what actually gets equipped, immediately (via {@link ServerEvents#equipLoadout})
 * as well as on every respawn. {@code select} applies a
 * preset's six items into the player's own loadout as a starting point.
 * {@code assign} is checked against that slot's whitelist server-side -
 * the item picker only ever offers whitelisted items, but this is the
 * actual enforcement boundary (a hand-typed command can't bypass it).
 */
public final class ClassCommand {

    private static final SuggestionProvider<CommandSourceStack> SLOT_KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(LoadoutSlot.values()).map(LoadoutSlot::key), builder);
    private static final SuggestionProvider<CommandSourceStack> CLASS_SLOT_KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.concat(java.util.stream.Stream.of("icon"),
                            Arrays.stream(LoadoutSlot.values()).map(LoadoutSlot::key)),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("class")
                .then(Commands.literal("editor")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> editor(ctx)))
                .then(Commands.literal("force")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> forceEditor(ctx)))
                .then(Commands.literal("save")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", UuidArgument.uuid())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> saveName(ctx)))))
                .then(Commands.literal("save_slot")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", UuidArgument.uuid())
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(CLASS_SLOT_KEYS)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> saveSlot(ctx))))))
                .then(Commands.literal("delete")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", UuidArgument.uuid()).executes(ctx -> delete(ctx))))
                .then(Commands.literal("whitelist")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> whitelistEditor(ctx))
                        .then(Commands.literal("add")
                                .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> whitelistAdd(ctx)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> whitelistRemove(ctx)))))
                        .then(Commands.literal("add_held")
                                .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                                        .executes(ctx -> whitelistAddHeld(ctx))))
                        .then(Commands.literal("register_held")
                                .then(Commands.argument("id", UuidArgument.uuid())
                                        .executes(ctx -> whitelistRegisterHeld(ctx))))
                        .then(Commands.literal("delete_variant")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(ctx -> whitelistDeleteVariant(ctx))))
                        .then(Commands.literal("ammo")
                                .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("ammoItem", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                        .executes(ctx -> whitelistAmmo(ctx))))))))
                .then(Commands.literal("protect")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> protectEditor(ctx))
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> protectAdd(ctx))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> protectRemove(ctx)))))
                .then(Commands.literal("spawnkit")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> spawnKitEditor(ctx))
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                        .executes(ctx -> spawnKitAdd(ctx)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> spawnKitRemove(ctx)))))
                .then(Commands.literal("hammerblocks")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> hammerBlocksEditor(ctx))
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", ResourceLocationArgument.id())
                                        .executes(ctx -> hammerBlocksAdd(ctx))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", ResourceLocationArgument.id())
                                        .executes(ctx -> hammerBlocksRemove(ctx)))))
                .then(Commands.literal("guardspawner")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("config")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("entityType", ResourceLocationArgument.id())
                                .then(Commands.argument("delaySeconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> guardSpawnerConfig(ctx))))))
                        .then(Commands.literal("add_item")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> guardSpawnerAddItem(ctx)))))
                        .then(Commands.literal("remove_item")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(ctx -> guardSpawnerRemoveItem(ctx)))))
                        .then(Commands.literal("pause").executes(ctx -> guardSpawnerPause(ctx)))
                        .then(Commands.literal("resume").executes(ctx -> guardSpawnerResume(ctx)))
                        .then(Commands.literal("clear").executes(ctx -> guardSpawnerClear(ctx))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> assign(ctx, true))
                                .then(Commands.literal("defer").executes(ctx -> assign(ctx, false))))))
                .then(Commands.literal("select")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(ctx -> select(ctx, true))
                                .then(Commands.literal("defer").executes(ctx -> select(ctx, false)))))
                .then(Commands.literal("clear")
                        .executes(ctx -> clear(ctx, true))
                        .then(Commands.literal("defer").executes(ctx -> clear(ctx, false))))
                .then(Commands.literal("forceselect")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(ctx -> forceSelect(ctx)))))
                .then(Commands.literal("forceassign")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> forceAssign(ctx))))))
                .then(Commands.literal("forceselectall")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(ctx -> forceSelectAll(ctx))))
                .then(Commands.literal("forceassignall")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> forceAssignAll(ctx)))))
                .then(Commands.literal("forceselectteam")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("team", TeamArgument.team())
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(ctx -> forceSelectTeam(ctx)))))
                .then(Commands.literal("forceassignteam")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("team", TeamArgument.team())
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> forceAssignTeam(ctx)))))));
    }

    /** Opens the OP-only preset editor client-side; permission already enforced by the command node. */
    private static int editor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenClassEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    /** Opens the OP-only force-loadout editor client-side (GUI front end for {@code forceselect}/{@code forceassign}); permission already enforced by the command node. */
    private static int forceEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenForceLoadoutEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    /**
     * Creates (with all slots empty) or renames a preset, keeping its existing slots if it
     * already exists. Split from slot assignment (see {@link #saveSlot}) because the editor
     * used to send id+icon+all six slots+name as a single command - fine for bare item ids,
     * but an OP-registered held-item variant id (~58 chars each) across seven slots easily built
     * a command longer than the 256-character limit vanilla enforces on chat/command packets,
     * disconnecting the client with an EncoderException. Seven small commands instead of one big
     * one sidesteps that ceiling entirely (matches how every other multi-value edit in this mod
     * - whitelist/spawn kit/hammer blocks - is already one command per item, never a batch).
     */
    private static int saveName(CommandContext<CommandSourceStack> ctx) {
        UUID id = UuidArgument.getUuid(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        ClassDefinition existing = manager.get(id);
        ClassDefinition def = existing != null
                ? existing.withName(name)
                : new ClassDefinition(id, name, null, null, null, null, null, null, null, null, null, null, null);
        manager.saveOrUpdate(ctx.getSource().getServer(), def);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.class_saved", name), true);
        return 1;
    }

    /** Sets one slot (or {@code icon}) of an already-created preset - see {@link #saveName}. */
    private static int saveSlot(CommandContext<CommandSourceStack> ctx) {
        UUID id = UuidArgument.getUuid(ctx, "id");
        String slotKey = StringArgumentType.getString(ctx, "slot");
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        ClassDefinition existing = manager.get(id);
        if (existing == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        ClassDefinition def;
        if ("icon".equals(slotKey)) {
            def = existing.withIcon(item);
        } else {
            LoadoutSlot slot = LoadoutSlot.byKey(slotKey);
            if (slot == null) {
                return fail(ctx, "classloadout.msg.unknown_slot", slotKey);
            }
            def = existing.withSlot(slot, item);
        }
        manager.saveOrUpdate(ctx.getSource().getServer(), def);
        return 1;
    }

    /** minecraft:air is the "no item" sentinel sent by the item picker's clear slot. */
    @Nullable
    private static ResourceLocation noneIfAir(ResourceLocation loc) {
        return "minecraft".equals(loc.getNamespace()) && "air".equals(loc.getPath()) ? null : loc;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        UUID id = UuidArgument.getUuid(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        if (!LoadoutManager.get(server).delete(server, id)) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.class_deleted"), true);
        return 1;
    }

    /** Opens the OP-only whitelist editor client-side; permission already enforced by the command node. */
    private static int whitelistEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenWhitelistEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    @Nullable
    private static LoadoutSlot parseSlot(CommandContext<CommandSourceStack> ctx) {
        return LoadoutSlot.byKey(StringArgumentType.getString(ctx, "slot"));
    }

    private static int whitelistAdd(CommandContext<CommandSourceStack> ctx) {
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        LoadoutManager.get(ctx.getSource().getServer()).addToWhitelist(ctx.getSource().getServer(), slot, item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.whitelist_added", item.toString()), true);
        return 1;
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> ctx) {
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        LoadoutManager.get(ctx.getSource().getServer()).removeFromWhitelist(ctx.getSource().getServer(), slot, item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.whitelist_removed", item.toString()), true);
        return 1;
    }

    /**
     * Whitelists the exact item (item, count and full tag - enchantments, custom name,
     * TACZ gun attachments, ...) currently in the OP's main hand, under a fresh synthetic
     * id, instead of just the bare item type. Sent by the whitelist editor's "Add Held
     * Item" button, not typed by hand.
     */
    private static int whitelistAddHeld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return fail(ctx, "classloadout.msg.hand_empty");
        }
        Component heldName = held.getHoverName();
        LoadoutManager.get(ctx.getSource().getServer()).addHeldItemToWhitelist(ctx.getSource().getServer(), slot, held);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.held_item_whitelisted", heldName), true);
        return 1;
    }

    /**
     * Registers the exact item in the OP's main hand as a reusable NBT-bearing variant,
     * under a client-supplied id (so the ammo grant popup that sent this command knows the
     * resulting id right away), without whitelisting it anywhere - used to pick a specific
     * NBT-bearing ammo item for an ammo grant's {@code ammoItem}, not a whitelist entry.
     */
    private static int whitelistRegisterHeld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID id = UuidArgument.getUuid(ctx, "id");
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return fail(ctx, "classloadout.msg.hand_empty");
        }
        Component heldName = held.getHoverName();
        LoadoutManager.get(ctx.getSource().getServer()).registerItemVariant(ctx.getSource().getServer(), id, held);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.held_item_registered", heldName), true);
        return 1;
    }

    /** Permanently removes a registered held-item variant from the catalog (see {@link LoadoutManager#deleteItemVariant}). */
    private static int whitelistDeleteVariant(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        LoadoutManager.get(ctx.getSource().getServer()).deleteItemVariant(ctx.getSource().getServer(), id);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.variant_deleted", id.toString()), true);
        return 1;
    }

    /**
     * Adds (or, with count 0, removes) one ammo-grant entry on an already-whitelisted (slot,
     * item) pair: equipping that item (on respawn, or immediately via the loadout
     * station/locker) also gives the player {@code count} of {@code ammoItem} into their general
     * inventory. An item can have any number of these, one per distinct {@code ammoItem} - this
     * only ever touches the single (item, ammoItem) pair given, leaving any other ammo grants
     * already on {@code item} alone.
     */
    private static int whitelistAmmo(CommandContext<CommandSourceStack> ctx) {
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        if (!manager.isWhitelisted(slot, item)) {
            return fail(ctx, "classloadout.msg.not_whitelisted", item.toString());
        }
        ResourceLocation ammoItem = ResourceLocationArgument.getId(ctx, "ammoItem");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        if (count <= 0) {
            manager.removeAmmoGrantEntry(ctx.getSource().getServer(), slot, item, ammoItem);
            ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.ammo_grant_cleared",
                    ammoItem.toString(), item.toString()), true);
        } else {
            manager.setAmmoGrantEntry(ctx.getSource().getServer(), slot, item, ammoItem, count);
            ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.ammo_grant_set",
                    item.toString(), count, ammoItem.toString()), true);
        }
        return 1;
    }

    /** Opens the OP-only protected-items editor client-side; permission already enforced by the command node. */
    private static int protectEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenProtectedItemsEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int protectAdd(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        LoadoutManager.get(ctx.getSource().getServer()).addProtectedItem(ctx.getSource().getServer(), item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.protect_added", item.toString()), true);
        return 1;
    }

    private static int protectRemove(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        LoadoutManager.get(ctx.getSource().getServer()).removeProtectedItem(ctx.getSource().getServer(), item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.protect_removed", item.toString()), true);
        return 1;
    }

    /** Opens the OP-only spawn kit editor client-side; permission already enforced by the command node. */
    private static int spawnKitEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenSpawnKitEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    /** count 0 removes the entry instead (matches /class whitelist ammo's convention). */
    private static int spawnKitAdd(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        LoadoutManager.get(ctx.getSource().getServer()).setSpawnKitEntry(ctx.getSource().getServer(), item, count);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.spawnkit_set", count, item.toString()), true);
        return 1;
    }

    private static int spawnKitRemove(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        LoadoutManager.get(ctx.getSource().getServer()).removeSpawnKitEntry(ctx.getSource().getServer(), item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.spawnkit_removed", item.toString()), true);
        return 1;
    }

    /** Opens the OP-only hammer-blocks editor client-side; permission already enforced by the command node. */
    private static int hammerBlocksEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenHammerBlocksEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int hammerBlocksAdd(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        LoadoutManager.get(ctx.getSource().getServer()).addHammerBlock(ctx.getSource().getServer(), block);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.hammerblocks_added", block.toString()), true);
        return 1;
    }

    private static int hammerBlocksRemove(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation block = ResourceLocationArgument.getId(ctx, "block");
        LoadoutManager.get(ctx.getSource().getServer()).removeHammerBlock(ctx.getSource().getServer(), block);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.hammerblocks_removed", block.toString()), true);
        return 1;
    }

    private static int guardSpawnerConfig(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ResourceLocation entityType = ResourceLocationArgument.getId(ctx, "entityType");
        int delaySeconds = IntegerArgumentType.getInteger(ctx, "delaySeconds");
        GlobalPos gpos = GlobalPos.of(ctx.getSource().getLevel().dimension(), pos);
        LoadoutManager.get(ctx.getSource().getServer()).setGuardSpawnerConfig(gpos, entityType, delaySeconds);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.guardspawner_configured",
                entityType.toString(), delaySeconds), true);
        return 1;
    }

    private static int guardSpawnerAddItem(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        GlobalPos gpos = GlobalPos.of(ctx.getSource().getLevel().dimension(), pos);
        LoadoutManager.get(ctx.getSource().getServer()).addGuardSpawnerItem(gpos, item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.guardspawner_item_added", item.toString()), true);
        return 1;
    }

    private static int guardSpawnerRemoveItem(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ResourceLocation item = ResourceLocationArgument.getId(ctx, "item");
        GlobalPos gpos = GlobalPos.of(ctx.getSource().getLevel().dimension(), pos);
        LoadoutManager.get(ctx.getSource().getServer()).removeGuardSpawnerItem(gpos, item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.guardspawner_item_removed", item.toString()), true);
        return 1;
    }

    /** Stops every guard spawner's watch/respawn tick globally, without touching per-block config. Existing spawned guards are left alone - see {@link #guardSpawnerClear}. */
    private static int guardSpawnerPause(CommandContext<CommandSourceStack> ctx) {
        LoadoutManager.get(ctx.getSource().getServer()).setGuardSpawningPaused(true);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.guardspawner_paused"), true);
        return 1;
    }

    private static int guardSpawnerResume(CommandContext<CommandSourceStack> ctx) {
        LoadoutManager.get(ctx.getSource().getServer()).setGuardSpawningPaused(false);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.guardspawner_resumed"), true);
        return 1;
    }

    /** Despawns every entity any guard spawner has ever spawned (identified by its tag), everywhere. Doesn't pause or otherwise change spawner config - pair with {@code /class guardspawner pause} first if they shouldn't just come back. */
    private static int guardSpawnerClear(CommandContext<CommandSourceStack> ctx) {
        int removed = ServerEvents.clearGuardSpawnerEntities(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.guardspawner_cleared", removed), true);
        return 1;
    }

    /**
     * Player self-service: assigns (or, with minecraft:air, clears) one slot
     * of their own loadout. The item must be on that slot's OP-curated
     * whitelist - this is the actual enforcement, not just the GUI filter.
     * Fails if an OP has locked this slot via {@code forceassign} (see
     * {@code LoadoutManager#isLocked}) - it stays fixed to whatever the OP
     * set until they free it. {@code immediate} equips the change into the
     * hotbar right away (the regular loadout station); with {@code false}
     * (the "defer" command variant, sent by the deferred loadout locker)
     * only the saved data changes and the hotbar is left alone until the
     * next respawn.
     */
    private static int assign(CommandContext<CommandSourceStack> ctx, boolean immediate) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        if (manager.isLocked(player.getUUID(), slot)) {
            return fail(ctx, "classloadout.msg.slot_locked",
                    Component.translatable("classloadout.gui.slot_" + slot.key()));
        }
        if (item != null && !manager.isWhitelisted(slot, item)) {
            return fail(ctx, "classloadout.msg.not_whitelisted", item.toString());
        }
        manager.setSlot(ctx.getSource().getServer(), player, slot, item);
        if (immediate) {
            ServerEvents.equipLoadout(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.slot_set",
                Component.translatable("classloadout.gui.slot_" + slot.key())), false);
        return 1;
    }

    /**
     * Applies a preset to the player's own loadout as a starting point (they can keep tweaking
     * individual slots afterward). Any slot the player has locked (see {@code
     * LoadoutManager#isLocked}) keeps its OP-forced value instead of taking the preset's. See
     * {@link #assign} for {@code immediate}.
     */
    private static int select(CommandContext<CommandSourceStack> ctx, boolean immediate) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID id = UuidArgument.getUuid(ctx, "id");
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        ClassDefinition def = manager.get(id);
        if (def == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        manager.applyPresetSelfService(ctx.getSource().getServer(), player, id);
        if (immediate) {
            ServerEvents.equipLoadout(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.preset_applied", def.name()), false);
        return 1;
    }

    /**
     * OP-only: applies a preset onto another player's personal loadout and equips it
     * immediately (same {@link ServerEvents#equipLoadout(ServerPlayer)} path as the loadout
     * station - inventory clear and ammo grants included, per that method's own rules) without
     * the target needing to touch anything themselves. Unlike {@link #select}, there's no
     * {@code defer} variant here - a target picked by an OP has no "locker" use case.
     */
    private static int forceSelect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        UUID id = UuidArgument.getUuid(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        ClassDefinition def = LoadoutManager.get(server).get(id);
        if (def == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        applyForceSelect(server, target, def);
        String targetName = target.getGameProfile().getName();
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.force_select_applied",
                def.name(), targetName), true);
        return 1;
    }

    /** Every-online-player counterpart to {@link #forceSelect} - used when the force-loadout GUI's target-name field is left blank. */
    private static int forceSelectAll(CommandContext<CommandSourceStack> ctx) {
        UUID id = UuidArgument.getUuid(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        ClassDefinition def = LoadoutManager.get(server).get(id);
        if (def == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer target : players) {
            applyForceSelect(server, target, def);
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.force_select_applied_all",
                def.name(), count), true);
        return count;
    }

    /**
     * Also locks every slot the preset gives a value to (see {@code LoadoutManager#lockSlot}) -
     * the player can't self-service-change it back until an OP frees it. Locks before applying
     * the preset, not after - {@code applyPreset}'s own sync push needs the updated lock state
     * already in place to reach the client in the same packet.
     */
    private static void applyForceSelect(MinecraftServer server, ServerPlayer target, ClassDefinition def) {
        LoadoutManager manager = LoadoutManager.get(server);
        PersonalLoadout applied = PersonalLoadout.fromClass(def);
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (applied.get(slot) != null) {
                manager.lockSlot(target.getUUID(), slot);
            }
        }
        manager.applyPreset(server, target, def.id());
        ServerEvents.equipLoadout(target);
        target.sendSystemMessage(Component.translatable("classloadout.msg.force_select_notice", def.name()));
    }

    /** Every-currently-online-member-of-a-team counterpart to {@link #forceSelect}. */
    private static int forceSelectTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerTeam team = TeamArgument.getTeam(ctx, "team");
        UUID id = UuidArgument.getUuid(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        ClassDefinition def = LoadoutManager.get(server).get(id);
        if (def == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        List<ServerPlayer> players = onlinePlayersOnTeam(server, team);
        for (ServerPlayer target : players) {
            applyForceSelect(server, target, def);
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.force_select_applied_team",
                def.name(), team.getName(), count), true);
        return count;
    }

    /** Online members of {@code team} (offline teammates are simply skipped - nothing to equip). */
    private static List<ServerPlayer> onlinePlayersOnTeam(MinecraftServer server, PlayerTeam team) {
        List<ServerPlayer> result = new ArrayList<>();
        for (String name : team.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * OP-only: force-sets a single slot of another player's loadout and equips it immediately -
     * the single-slot counterpart to {@link #forceSelect}, for touching up one item (e.g. just
     * the helmet) without overwriting the rest of their loadout with a whole preset. Bypasses
     * that slot's whitelist, same as {@link #forceSelect}/presets in general - an OP action, not
     * player self-service, so the whitelist (which exists to constrain the self-service picker)
     * doesn't apply here either.
     */
    private static int forceAssign(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        MinecraftServer server = ctx.getSource().getServer();
        applyForceAssign(server, target, slot, item);
        String targetName = target.getGameProfile().getName();
        Component slotName = Component.translatable("classloadout.gui.slot_" + slot.key());
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.force_assign_applied",
                slotName, targetName), true);
        return 1;
    }

    /** Every-online-player counterpart to {@link #forceAssign} - used when the force-loadout GUI's target-name field is left blank. */
    private static int forceAssignAll(CommandContext<CommandSourceStack> ctx) {
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer target : players) {
            applyForceAssign(server, target, slot, item);
        }
        int count = players.size();
        Component slotName = Component.translatable("classloadout.gui.slot_" + slot.key());
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.force_assign_applied_all",
                slotName, count), true);
        return count;
    }

    /**
     * A non-air item locks the slot (see {@code LoadoutManager#lockSlot}) so the player can't
     * self-service-change it back; force-assigning {@code minecraft:air} to an already-locked
     * slot unlocks it instead - the intended way for an OP to free one back up.
     */
    private static void applyForceAssign(MinecraftServer server, ServerPlayer target, LoadoutSlot slot,
            @Nullable ResourceLocation item) {
        LoadoutManager manager = LoadoutManager.get(server);
        // Lock/unlock before setSlot, not after - setSlot's own sync push (see LoadoutManager#sendTo)
        // needs the updated lock state to already be in place to reach the client in the same packet.
        if (item != null) {
            manager.lockSlot(target.getUUID(), slot);
        } else {
            manager.unlockSlot(target.getUUID(), slot);
        }
        manager.setSlot(server, target, slot, item);
        ServerEvents.equipLoadout(target);
        Component slotName = Component.translatable("classloadout.gui.slot_" + slot.key());
        target.sendSystemMessage(Component.translatable("classloadout.msg.force_assign_notice", slotName));
    }

    /** Every-currently-online-member-of-a-team counterpart to {@link #forceAssign}. */
    private static int forceAssignTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerTeam team = TeamArgument.getTeam(ctx, "team");
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerPlayer> players = onlinePlayersOnTeam(server, team);
        for (ServerPlayer target : players) {
            applyForceAssign(server, target, slot, item);
        }
        int count = players.size();
        Component slotName = Component.translatable("classloadout.gui.slot_" + slot.key());
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.force_assign_applied_team",
                slotName, team.getName(), count), true);
        return count;
    }

    /** Locked slots (see {@code LoadoutManager#isLocked}) keep their OP-forced value instead of being wiped. See {@link #assign} for {@code immediate}. */
    private static int clear(CommandContext<CommandSourceStack> ctx, boolean immediate) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LoadoutManager.get(ctx.getSource().getServer()).clearPersonalLoadoutSelfService(ctx.getSource().getServer(), player);
        if (immediate) {
            ServerEvents.equipLoadout(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.class_cleared"), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private ClassCommand() {}
}
