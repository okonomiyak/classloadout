package uk.iwaservice.classloadout.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Only classloaded by {@link uk.iwaservice.classloadout.compat.TaczCompat}
 * when TACZ is present. TACZ's individual guns (AK47, M4, ...) are entries
 * in its data-driven "common gun index" (works on both logical sides, unlike
 * the client-only display index), not separate registered Minecraft items -
 * this resolves between the two worlds.
 */
public final class TaczGunResolver {

    public static boolean isGunId(ResourceLocation id) {
        return TimelessAPI.getCommonGunIndex(id).isPresent();
    }

    public static ItemStack buildStack(ResourceLocation id) {
        return GunItemBuilder.create().setId(id).build();
    }

    public static List<ResourceLocation> allGunIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ?> entry : TimelessAPI.getAllCommonGunIndex()) {
            ids.add(entry.getKey());
        }
        return ids;
    }

    private TaczGunResolver() {}
}
