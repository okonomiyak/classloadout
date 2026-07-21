package uk.iwaservice.classloadout;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.compat.TaczCompat;

import javax.annotation.Nullable;

/**
 * Turns a stored slot {@link ResourceLocation} into an actual {@link ItemStack},
 * used everywhere a class/loadout/whitelist entry needs to be rendered or
 * equipped. Most ids are plain registered items - but a handful of weapon
 * mods don't register one item per weapon, so this is the one place that
 * knows to ask a compat module instead. Currently only TACZ needs this (its
 * guns are a single generic item selected by a {@code GunId} NBT tag, not by
 * registry id - see {@link TaczCompat}); everything else, including
 * SuperbWarfare, uses ordinary registered items and needs no special case.
 */
public final class ItemResolver {

    /** Null if {@code id} can't be resolved on this side (e.g. its mod isn't installed). */
    @Nullable
    public static ItemStack resolve(ResourceLocation id) {
        ItemStack gunStack = TaczCompat.buildGunStack(id);
        if (gunStack != null) {
            return gunStack;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : new ItemStack(item);
    }

    public static boolean isAvailable(ResourceLocation id) {
        return TaczCompat.isGunId(id) || ForgeRegistries.ITEMS.containsKey(id);
    }

    private ItemResolver() {}
}
