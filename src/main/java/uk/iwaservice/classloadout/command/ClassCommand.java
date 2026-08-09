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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.ServerEvents;
import uk.iwaservice.classloadout.loadout.ClassDefinition;
import uk.iwaservice.classloadout.loadout.LoadoutManager;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.Arrays;
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
 * preset's five items into the player's own loadout as a starting point.
 * {@code assign} is checked against that slot's whitelist server-side -
 * the item picker only ever offers whitelisted items, but this is the
 * actual enforcement boundary (a hand-typed command can't bypass it).
 */
public final class ClassCommand {

    private static final SuggestionProvider<CommandSourceStack> SLOT_KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(LoadoutSlot.values()).map(LoadoutSlot::key), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("class")
                .then(Commands.literal("editor")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> editor(ctx)))
                .then(Commands.literal("save")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", UuidArgument.uuid())
                        .then(Commands.argument("icon", ResourceLocationArgument.id())
                        .then(Commands.argument("main", ResourceLocationArgument.id())
                        .then(Commands.argument("sidearm", ResourceLocationArgument.id())
                        .then(Commands.argument("throwable", ResourceLocationArgument.id())
                        .then(Commands.argument("gadget", ResourceLocationArgument.id())
                        .then(Commands.argument("melee", ResourceLocationArgument.id())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> save(ctx)))))))))))
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
                        .then(Commands.literal("defer").executes(ctx -> clear(ctx, false)))));
    }

    /** Opens the OP-only preset editor client-side; permission already enforced by the command node. */
    private static int editor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NetworkHandler.sendOpenClassEditor(ctx.getSource().getPlayerOrException());
        return 1;
    }

    /** Creates or (if the id already exists) overwrites a preset definition in one atomic command. */
    private static int save(CommandContext<CommandSourceStack> ctx) {
        UUID id = UuidArgument.getUuid(ctx, "id");
        ResourceLocation icon = noneIfAir(ResourceLocationArgument.getId(ctx, "icon"));
        ResourceLocation main = noneIfAir(ResourceLocationArgument.getId(ctx, "main"));
        ResourceLocation sidearm = noneIfAir(ResourceLocationArgument.getId(ctx, "sidearm"));
        ResourceLocation throwable = noneIfAir(ResourceLocationArgument.getId(ctx, "throwable"));
        ResourceLocation gadget = noneIfAir(ResourceLocationArgument.getId(ctx, "gadget"));
        ResourceLocation melee = noneIfAir(ResourceLocationArgument.getId(ctx, "melee"));
        String name = StringArgumentType.getString(ctx, "name");

        ClassDefinition def = new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, melee);
        LoadoutManager.get(ctx.getSource().getServer()).saveOrUpdate(ctx.getSource().getServer(), def);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.class_saved", name), true);
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
     * Attaches (or, with count 0, clears) an ammo grant to an already-whitelisted
     * (slot, item) pair: equipping that item on respawn will also give the
     * player {@code count} of {@code ammoItem} into their general inventory.
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
            manager.clearAmmoGrant(ctx.getSource().getServer(), slot, item);
            ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.ammo_grant_cleared", item.toString()), true);
        } else {
            manager.setAmmoGrant(ctx.getSource().getServer(), slot, item, ammoItem, count);
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

    /**
     * Player self-service: assigns (or, with minecraft:air, clears) one slot
     * of their own loadout. The item must be on that slot's OP-curated
     * whitelist - this is the actual enforcement, not just the GUI filter.
     * {@code immediate} equips the change into the hotbar right away (the
     * regular loadout station); with {@code false} (the "defer" command
     * variant, sent by the deferred loadout locker) only the saved data
     * changes and the hotbar is left alone until the next respawn.
     */
    private static int assign(CommandContext<CommandSourceStack> ctx, boolean immediate) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LoadoutSlot slot = parseSlot(ctx);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", StringArgumentType.getString(ctx, "slot"));
        }
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
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

    /** Applies a preset to the player's own loadout as a starting point (they can keep tweaking individual slots afterward). See {@link #assign} for {@code immediate}. */
    private static int select(CommandContext<CommandSourceStack> ctx, boolean immediate) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID id = UuidArgument.getUuid(ctx, "id");
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        ClassDefinition def = manager.get(id);
        if (def == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        manager.applyPreset(ctx.getSource().getServer(), player, id);
        if (immediate) {
            ServerEvents.equipLoadout(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.preset_applied", def.name()), false);
        return 1;
    }

    /** See {@link #assign} for {@code immediate}. */
    private static int clear(CommandContext<CommandSourceStack> ctx, boolean immediate) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LoadoutManager.get(ctx.getSource().getServer()).clearPersonalLoadout(ctx.getSource().getServer(), player);
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
