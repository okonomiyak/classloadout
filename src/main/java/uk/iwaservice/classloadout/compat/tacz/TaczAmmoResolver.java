package uk.iwaservice.classloadout.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
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

    private TaczAmmoResolver() {}
}
