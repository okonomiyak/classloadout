package uk.iwaservice.classloadout.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Sole gateway into the TACZ (gun mod) integration. The guard classes under
 * {@code compat.tacz} reference TACZ API types and are only classloaded
 * behind the {@code isLoaded} check below, so the mod works unchanged when
 * TACZ is not installed.
 *
 * <p>TACZ doesn't register one Minecraft item per gun - every gun is the
 * same generic item (e.g. {@code tacz:modern_kinetic_gun}) with a
 * {@code GunId} NBT tag selecting which one it actually is, resolved via
 * TACZ's data-driven gun index. {@link #isGunId} / {@link #buildGunStack} /
 * {@link #allGunIds} let the rest of the mod treat a gun id exactly like any
 * other selectable {@link ResourceLocation}.
 */
public final class TaczCompat {

    private static boolean isLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    /** No-op (and safe to call unconditionally) when TACZ isn't installed. */
    public static void resupply(ServerPlayer player, int amount) {
        if (!isLoaded()) {
            return;
        }
        uk.iwaservice.classloadout.compat.tacz.TaczAmmoResupplier.resupply(player, amount);
    }

    /** True if {@code id} is a registered TACZ gun id (as opposed to a plain item id). */
    public static boolean isGunId(ResourceLocation id) {
        return isLoaded() && uk.iwaservice.classloadout.compat.tacz.TaczGunResolver.isGunId(id);
    }

    /** Builds a fully-configured ItemStack for the given gun id, or null if it isn't a known gun (or TACZ isn't installed). */
    @Nullable
    public static ItemStack buildGunStack(ResourceLocation id) {
        if (!isLoaded()) {
            return null;
        }
        return uk.iwaservice.classloadout.compat.tacz.TaczGunResolver.buildStack(id);
    }

    /** Every registered TACZ gun id; empty if TACZ isn't installed. */
    public static List<ResourceLocation> allGunIds() {
        if (!isLoaded()) {
            return List.of();
        }
        return uk.iwaservice.classloadout.compat.tacz.TaczGunResolver.allGunIds();
    }

    private TaczCompat() {}
}
