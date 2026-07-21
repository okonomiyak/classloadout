package uk.iwaservice.classloadout;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import uk.iwaservice.classloadout.network.NetworkHandler;

/**
 * Standalone loadout-class mod: no dependency on any squad/party mod. Admins
 * define classes via a GUI editor; players pick one from the death screen
 * and it is auto-equipped into hotbar slots 0-4 on every respawn.
 */
@Mod(ClassLoadoutMod.MODID)
public class ClassLoadoutMod {
    public static final String MODID = "classloadout";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClassLoadoutMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::buildCreativeTabs);
        ModRegistry.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }

    private void buildCreativeTabs(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.HEALTH_PACK_ITEM.get());
            event.accept(ModRegistry.AMMO_PACK_ITEM.get());
        }
    }
}
