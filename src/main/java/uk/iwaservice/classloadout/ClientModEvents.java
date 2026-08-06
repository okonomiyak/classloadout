package uk.iwaservice.classloadout;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.iwaservice.classloadout.client.ResupplyPackRenderer;

/** Mod-bus client events: entity renderer registration (needs the mod bus, not the Forge bus). */
@Mod.EventBusSubscriber(modid = ClassLoadoutMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.HEALTH_PACK.get(),
                ctx -> new ResupplyPackRenderer<>(ctx, ModRegistry.HEALTH_PACK_ITEM));
        event.registerEntityRenderer(ModRegistry.AMMO_PACK.get(),
                ctx -> new ResupplyPackRenderer<>(ctx, ModRegistry.AMMO_PACK_ITEM));
        event.registerEntityRenderer(ModRegistry.THROWN_HEALTH_PACK.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModRegistry.THROWN_AMMO_PACK.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModRegistry.COVER.get(),
                ctx -> new ResupplyPackRenderer<>(ctx, ModRegistry.COVER_ITEM));
    }

    private ClientModEvents() {}
}
