package uk.iwaservice.classloadout;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client events: entity renderer registration (needs the mod bus, not the Forge bus). */
@Mod.EventBusSubscriber(modid = ClassLoadoutMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.THROWN_HEALTH_PACK.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModRegistry.THROWN_AMMO_PACK.get(), ThrownItemRenderer::new);
    }

    private ClientModEvents() {}
}
