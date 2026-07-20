package uk.iwaservice.classloadout.command;

import com.mojang.brigadier.CommandDispatcher;
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
 * <p>Two independent things live under here: OP-managed <b>presets</b>
 * (editor/save/delete) and each player's own self-service <b>personal
 * loadout</b> (assign/select/clear) - the personal loadout, not any preset,
 * is what actually gets equipped on respawn. {@code select} applies a
 * preset's five items into the player's own loadout as a starting point.
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
                .then(Commands.literal("assign")
                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOT_KEYS)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> assign(ctx)))))
                .then(Commands.literal("select")
                        .then(Commands.argument("id", UuidArgument.uuid()).executes(ctx -> select(ctx))))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx))));
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

    /** Player self-service: assigns (or, with minecraft:air, clears) one slot of their own loadout. */
    private static int assign(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String slotKey = StringArgumentType.getString(ctx, "slot");
        LoadoutSlot slot = LoadoutSlot.byKey(slotKey);
        if (slot == null) {
            return fail(ctx, "classloadout.msg.unknown_slot", slotKey);
        }
        ResourceLocation item = noneIfAir(ResourceLocationArgument.getId(ctx, "item"));
        LoadoutManager.get(ctx.getSource().getServer()).setSlot(ctx.getSource().getServer(), player, slot, item);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.slot_set",
                Component.translatable("classloadout.gui.slot_" + slot.key())), false);
        return 1;
    }

    /** Applies a preset to the player's own loadout as a starting point (they can keep tweaking individual slots afterward). */
    private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID id = UuidArgument.getUuid(ctx, "id");
        LoadoutManager manager = LoadoutManager.get(ctx.getSource().getServer());
        ClassDefinition def = manager.get(id);
        if (def == null) {
            return fail(ctx, "classloadout.msg.class_not_found");
        }
        manager.applyPreset(ctx.getSource().getServer(), player, id);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.preset_applied", def.name()), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LoadoutManager.get(ctx.getSource().getServer()).clearPersonalLoadout(ctx.getSource().getServer(), player);
        ctx.getSource().sendSuccess(() -> Component.translatable("classloadout.msg.class_cleared"), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }

    private ClassCommand() {}
}
