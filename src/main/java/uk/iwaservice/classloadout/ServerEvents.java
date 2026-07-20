package uk.iwaservice.classloadout;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.command.ClassCommand;
import uk.iwaservice.classloadout.loadout.ClassDefinition;
import uk.iwaservice.classloadout.loadout.LoadoutManager;

/** Forge-bus event handlers: command registration, respawn equip, login sync. */
public final class ServerEvents {

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

    /**
     * Overwrites hotbar slots 0-4 with the player's selected class's
     * main/sidearm/throwable/gadget/melee items (in that fixed order),
     * clearing any slot the class leaves unset. Overwriting rather than
     * adding keeps the icon-row order deterministic and avoids duplicate
     * gear if the keepInventory gamerule is on.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ClassDefinition def = LoadoutManager.get(player.server).getSelectedDefinition(player.getUUID());
        if (def == null) {
            return;
        }
        ResourceLocation[] slots = {def.main(), def.sidearm(), def.throwable(), def.gadget(), def.melee()};
        for (int i = 0; i < slots.length; i++) {
            Item item = slots[i] == null ? null : ForgeRegistries.ITEMS.getValue(slots[i]);
            player.getInventory().setItem(i, item == null ? ItemStack.EMPTY : new ItemStack(item));
        }
    }

    private ServerEvents() {}
}
