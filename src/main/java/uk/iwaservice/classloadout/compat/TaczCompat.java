package uk.iwaservice.classloadout.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Sole gateway into the TACZ (gun mod) ammo-resupply integration. The guard
 * class under {@code compat.tacz} references TACZ API types and is only
 * classloaded behind the {@code isLoaded} check below, so the mod works
 * unchanged when TACZ is not installed.
 */
public final class TaczCompat {

    /** No-op (and safe to call unconditionally) when TACZ isn't installed. */
    public static void resupply(ServerPlayer player, int amount) {
        if (!ModList.get().isLoaded("tacz")) {
            return;
        }
        uk.iwaservice.classloadout.compat.tacz.TaczAmmoResupplier.resupply(player, amount);
    }

    private TaczCompat() {}
}
