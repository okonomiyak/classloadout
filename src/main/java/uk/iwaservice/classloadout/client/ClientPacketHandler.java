package uk.iwaservice.classloadout.client;

import net.minecraft.client.Minecraft;
import uk.iwaservice.classloadout.client.gui.ClassEditorScreen;
import uk.iwaservice.classloadout.client.gui.ProtectedItemsEditorScreen;
import uk.iwaservice.classloadout.client.gui.SpawnKitEditorScreen;
import uk.iwaservice.classloadout.client.gui.WhitelistEditorScreen;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;

/** Client-only entry points for the S2C packets. Never classloaded on a dedicated server. */
public final class ClientPacketHandler {

    public static void handleLoadoutSync(LoadoutSyncPacket msg) {
        LoadoutClientData.applySync(msg.classes(), msg.personal(), msg.whitelists(), msg.ammoGrants(), msg.variants(),
                msg.protectedItems(), msg.spawnKit());
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

    private ClientPacketHandler() {}
}
