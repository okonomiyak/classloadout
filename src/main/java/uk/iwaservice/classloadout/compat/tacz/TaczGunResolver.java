package uk.iwaservice.classloadout.compat.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.network.chat.Component;
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

    /**
     * Extra tooltip lines for a TACZ gun stack: its gun id, ammo id (with current loaded
     * count), and every non-empty attachment - none of this is covered by the item's own
     * {@code appendHoverText} (TACZ shows it via its own HUD overlay instead), so the
     * whitelist editor's tooltip would otherwise just show the bare item name for guns.
     * Empty if {@code stack} isn't a TACZ gun.
     */
    public static List<Component> describeGunTooltip(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        ResourceLocation gunId = gun.getGunId(stack);
        lines.add(Component.literal("Gun: " + gunId));
        TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
            ResourceLocation ammoId = index.getGunData().getAmmoId();
            if (ammoId != null) {
                lines.add(Component.literal("Ammo: " + ammoId + " (" + gun.getCurrentAmmoCount(stack) + ")"));
            }
        });
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ResourceLocation attachmentId = gun.getAttachmentId(stack, type);
            if (attachmentId != null && !DefaultAssets.isEmptyAttachmentId(attachmentId)) {
                lines.add(Component.literal(type.name() + ": " + attachmentId));
            }
        }
        return lines;
    }

    private TaczGunResolver() {}
}
