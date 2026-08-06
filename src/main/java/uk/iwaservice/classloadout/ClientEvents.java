package uk.iwaservice.classloadout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.client.gui.LoadoutScreen;

@Mod.EventBusSubscriber(modid = ClassLoadoutMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LoadoutClientData.clear();
    }

    /**
     * Right-clicking a placed loadout station opens the same self-service
     * {@link LoadoutScreen} as the death screen's button - purely client-side
     * (the loadout data is already synced), so there's nothing for the server
     * to authorize and no reason to route this through a packet.
     *
     * <p>{@code RightClickBlock} fires once per hand for a single physical
     * click; without the {@code MAIN_HAND} guard below, a single click could
     * call {@code setScreen(new LoadoutScreen(...))} twice for the same
     * click (crashed with an NPE from a stale {@code Screen.minecraft}
     * reference in testing) - only ever react to one of the two firings.
     */
    @SubscribeEvent
    public static void onRightClickLoadoutStation(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getLevel().getBlockState(event.getPos()).is(ModRegistry.LOADOUT_STATION.get())) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getInstance().setScreen(new LoadoutScreen(null));
    }

    /** Adds a "Loadout" button to the vanilla death screen, in the corner to avoid the Respawn/Title stack. */
    @SubscribeEvent
    public static void onDeathScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen deathScreen)) {
            return;
        }
        int w = 100;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        event.addListener(Button.builder(Component.translatable("classloadout.gui.loadout_button"),
                        b -> Minecraft.getInstance().setScreen(new LoadoutScreen(deathScreen)))
                .bounds(screenWidth - w - 10, 10, w, 20).build());
    }

    private ClientEvents() {}
}
