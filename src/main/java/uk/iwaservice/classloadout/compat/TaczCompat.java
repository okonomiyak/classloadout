package uk.iwaservice.classloadout.compat;

import net.minecraft.network.chat.Component;
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
 * other selectable {@link ResourceLocation}. Ammo works the same way (one
 * generic ammo item + an {@code AmmoId} NBT tag) - {@link #isAmmoId} /
 * {@link #buildAmmoStack} / {@link #allAmmoIds} are the ammo equivalents.
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
        if (!isLoaded() || !uk.iwaservice.classloadout.compat.tacz.TaczGunResolver.isGunId(id)) {
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

    /** True if {@code id} is a registered TACZ ammo id (as opposed to a plain item id). */
    public static boolean isAmmoId(ResourceLocation id) {
        return isLoaded() && uk.iwaservice.classloadout.compat.tacz.TaczAmmoResolver.isAmmoId(id);
    }

    /** Builds a fully-configured ItemStack for the given ammo id, or null if it isn't a known ammo type (or TACZ isn't installed). */
    @Nullable
    public static ItemStack buildAmmoStack(ResourceLocation id) {
        if (!isLoaded() || !uk.iwaservice.classloadout.compat.tacz.TaczAmmoResolver.isAmmoId(id)) {
            return null;
        }
        return uk.iwaservice.classloadout.compat.tacz.TaczAmmoResolver.buildStack(id);
    }

    /** Every registered TACZ ammo id; empty if TACZ isn't installed. */
    public static List<ResourceLocation> allAmmoIds() {
        if (!isLoaded()) {
            return List.of();
        }
        return uk.iwaservice.classloadout.compat.tacz.TaczAmmoResolver.allAmmoIds();
    }

    /** Extra tooltip lines (gun id, ammo, attachments) for a TACZ gun stack; empty if TACZ isn't installed or the stack isn't a gun. */
    public static List<Component> describeGunTooltip(ItemStack stack) {
        if (!isLoaded()) {
            return List.of();
        }
        return uk.iwaservice.classloadout.compat.tacz.TaczGunResolver.describeGunTooltip(stack);
    }

    /** Extra tooltip line (loaded ammo type + count) for a TACZ ammo box stack; empty if TACZ isn't installed or the stack isn't an ammo box. */
    public static List<Component> describeAmmoBoxTooltip(ItemStack stack) {
        if (!isLoaded()) {
            return List.of();
        }
        return uk.iwaservice.classloadout.compat.tacz.TaczAmmoResolver.describeAmmoBoxTooltip(stack);
    }

    private TaczCompat() {}
}
