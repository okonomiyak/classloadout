package uk.iwaservice.classloadout.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.classloadout.client.gui.ClassEditorScreen;
import uk.iwaservice.classloadout.client.gui.ForceLoadoutScreen;
import uk.iwaservice.classloadout.client.gui.GuardSpawnerEditorScreen;
import uk.iwaservice.classloadout.client.gui.HammerBlocksEditorScreen;
import uk.iwaservice.classloadout.client.gui.ProtectedItemsEditorScreen;
import uk.iwaservice.classloadout.client.gui.SpawnKitEditorScreen;
import uk.iwaservice.classloadout.client.gui.WhitelistEditorScreen;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

import javax.annotation.Nullable;
import java.util.List;

/** Client-only entry points for the S2C packets. Never classloaded on a dedicated server. */
public final class ClientPacketHandler {

    public static void handleLoadoutSync(LoadoutSyncPacket msg) {
        LoadoutClientData.applySync(msg.classes(), msg.personal(), msg.whitelists(), msg.ammoGrants(), msg.variants(),
                msg.protectedItems(), msg.spawnKit(), msg.hammerBlocks(), msg.lockedSlots());
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenHammerBlocksEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new HammerBlocksEditorScreen());
        }
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenProtectedItemsEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new ProtectedItemsEditorScreen());
        }
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenSpawnKitEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new SpawnKitEditorScreen());
        }
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenClassEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new ClassEditorScreen());
        }
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenWhitelistEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new WhitelistEditorScreen());
        }
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenGuardSpawnerEditor(BlockPos pos, @Nullable ResourceLocation entityType,
            int delaySeconds, List<ResourceLocation> items) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new GuardSpawnerEditorScreen(pos, entityType, delaySeconds, items));
        }
    }

    /** Server already checked permission level before sending this; re-check defensively anyway. */
    public static void handleOpenForceLoadoutEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasPermissions(2)) {
            mc.setScreen(new ForceLoadoutScreen());
        }
    }

    private ClientPacketHandler() {}
}
