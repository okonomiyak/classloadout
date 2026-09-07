package uk.iwaservice.classloadout;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import uk.iwaservice.classloadout.network.NetworkHandler;

/**
 * Standalone loadout-class mod: no dependency on any squad/party mod. Admins
 * define classes via a GUI editor; players pick one from the death screen
 * and it is auto-equipped into hotbar slots 0-5 on every respawn.
 */
@Mod(ClassLoadoutMod.MODID)
public class ClassLoadoutMod {
    public static final String MODID = "classloadout";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClassLoadoutMod(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::buildCreativeTabs);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(NetworkHandler::register);
        ModRegistry.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModRegistry.HEALTH_PACK.get(), uk.iwaservice.classloadout.resupply.AbstractResupplyPackEntity.createAttributes().build());
        event.put(ModRegistry.AMMO_PACK.get(), uk.iwaservice.classloadout.resupply.AbstractResupplyPackEntity.createAttributes().build());
        event.put(ModRegistry.COVER.get(), uk.iwaservice.classloadout.cover.CoverEntity.createAttributes().build());
    }

    private void buildCreativeTabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.COMBAT) {
            event.accept(ModRegistry.HEALTH_PACK_ITEM.get());
            event.accept(ModRegistry.AMMO_PACK_ITEM.get());
            event.accept(ModRegistry.THROWN_HEALTH_PACK_ITEM.get());
            event.accept(ModRegistry.THROWN_AMMO_PACK_ITEM.get());
            event.accept(ModRegistry.COVER_ITEM.get());
            event.accept(ModRegistry.BANDAGE_ITEM.get());
        } else if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.LOADOUT_STATION_ITEM.get());
            event.accept(ModRegistry.LOADOUT_LOCKER_ITEM.get());
            event.accept(ModRegistry.GUARD_SPAWNER_ITEM.get());
        }
    }
}
