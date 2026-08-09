package uk.iwaservice.classloadout.compat.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Only classloaded by {@link uk.iwaservice.classloadout.compat.TaczCompat}
 * when TACZ is present. Same shape as {@link TaczGunResolver} but for
 * ammo: TACZ's individual ammo types (9mm, 12 gauge, ...) are entries in
 * its data-driven "common ammo index", not separate registered Minecraft
 * items - this resolves between the two worlds.
 */
public final class TaczAmmoResolver {

    public static boolean isAmmoId(ResourceLocation id) {
        return TimelessAPI.getCommonAmmoIndex(id).isPresent();
    }

    /** Count defaults to 1 - callers that need a specific count (e.g. an ammo grant) build their own stack from this template. */
    public static ItemStack buildStack(ResourceLocation id) {
        return AmmoItemBuilder.create().setId(id).setCount(1).build();
    }

    public static List<ResourceLocation> allAmmoIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ?> entry : TimelessAPI.getAllCommonAmmoIndex()) {
            ids.add(entry.getKey());
        }
        return ids;
    }

    /**
     * Extra tooltip line for a TACZ ammo box stack: which ammo type it's loaded with and
     * how much - the box's own tooltip doesn't say (TACZ conveys it visually via the box's
     * dye color + a HUD overlay instead). Empty if {@code stack} isn't an ammo box.
     */
    public static List<Component> describeAmmoBoxTooltip(ItemStack stack) {
        if (!(stack.getItem() instanceof IAmmoBox box)) {
            return List.of();
        }
        ResourceLocation ammoId = box.getAmmoId(stack);
        if (ammoId == null || ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return List.of(Component.literal("Ammo: (not loaded yet)"));
        }
        return List.of(Component.literal("Ammo: " + ammoId + " x" + box.getAmmoCount(stack)));
    }

    private TaczAmmoResolver() {}
}
