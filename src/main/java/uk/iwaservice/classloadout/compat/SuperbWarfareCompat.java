package uk.iwaservice.classloadout.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Sole gateway into the SuperbWarfare (gun mod) ammo-resupply integration.
 * The guard class under {@code compat.superbwarfare} references SuperbWarfare
 * API types and is only classloaded behind the {@code isLoaded} check below,
 * so the mod works unchanged when SuperbWarfare is not installed.
 */
public final class SuperbWarfareCompat {

    /** No-op (and safe to call unconditionally) when SuperbWarfare isn't installed. */
    public static void resupply(ServerPlayer player, int amount) {
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        uk.iwaservice.classloadout.compat.superbwarfare.SwAmmoResupplier.resupply(player, amount);
    }

    private SuperbWarfareCompat() {}
}
