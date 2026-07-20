package uk.iwaservice.classloadout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
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
