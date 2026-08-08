package uk.iwaservice.classloadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.compat.TaczCompat;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Turns a stored slot {@link ResourceLocation} into an actual {@link ItemStack},
 * used everywhere a class/loadout/whitelist entry needs to be rendered or
 * equipped. Most ids are plain registered items - but a handful of weapon
 * mods don't register one item per weapon (or per ammo type), so this is the
 * one place that knows to ask a compat module instead. Currently only TACZ
 * needs this (its guns and ammo are each a single generic item selected by a
 * {@code GunId}/{@code AmmoId} NBT tag, not by registry id - see
 * {@link TaczCompat}); everything else, including SuperbWarfare, uses
 * ordinary registered items and needs no special case.
 */
public final class ItemResolver {

    /** Null if {@code id} can't be resolved on this side (e.g. its mod isn't installed). */
    @Nullable
    public static ItemStack resolve(ResourceLocation id) {
        ItemStack gunStack = TaczCompat.buildGunStack(id);
        if (gunStack != null) {
            return gunStack;
        }
        ItemStack ammoStack = TaczCompat.buildAmmoStack(id);
        if (ammoStack != null) {
            return ammoStack;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : new ItemStack(item);
    }

    /**
     * Same as {@link #resolve(ResourceLocation)}, but first checks {@code variants} - the
     * server's {@code LoadoutManager.getItemVariants()} or the client's
     * {@code LoadoutClientData.getItemVariants()} - for an OP-registered "exact held item"
     * whitelist entry (full item+count+tag, not just a bare item id). {@code variants} may be
     * null or not contain {@code id}, in which case this behaves exactly like the 1-arg overload.
     *
     * <p>{@code saved} is the single {@link CompoundTag} instance stored in {@code variants} for
     * this id, shared by every caller that resolves it. {@code ItemStack.of(CompoundTag)} doesn't
     * deep-copy the tag it's built from, it keeps a reference to it - so without {@link
     * ItemStack#copy()} here, every player issued this item ends up with an {@code ItemStack}
     * whose NBT is literally the same object as everyone else's: TACZ writing a gun's current
     * ammo count on one player's stack mutates that shared tag and instantly changes it for every
     * other player holding "their own" copy too.
     */
    @Nullable
    public static ItemStack resolve(ResourceLocation id, @Nullable Map<ResourceLocation, CompoundTag> variants) {
        CompoundTag saved = variants == null ? null : variants.get(id);
        return saved != null ? ItemStack.of(saved).copy() : resolve(id);
    }

    public static boolean isAvailable(ResourceLocation id) {
        return TaczCompat.isGunId(id) || TaczCompat.isAmmoId(id) || ForgeRegistries.ITEMS.containsKey(id);
    }

    private ItemResolver() {}
}
