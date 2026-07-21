package uk.iwaservice.classloadout.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.compat.TaczCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The full pool of items an OP can browse when curating a slot whitelist or
 * editing a preset: every registered item in the {@code tacz},
 * {@code superbwarfare}, {@code minecraft} and {@code classloadout}
 * (the mod's own resupply pack items) namespaces, plus - if TACZ is loaded -
 * every individual TACZ gun id (TACZ doesn't register one item per gun; see
 * {@link TaczCompat}). Shared by {@link ItemPickerScreen} and
 * {@link WhitelistEditorScreen} so both grids list and search the exact same
 * candidates.
 */
final class ItemCatalog {

    private static final Set<String> ALLOWED_NAMESPACES = Set.of("tacz", "superbwarfare", "minecraft", "classloadout");

    static List<ResourceLocation> all() {
        Set<ResourceLocation> set = new LinkedHashSet<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc == null || item == Items.AIR || !ALLOWED_NAMESPACES.contains(loc.getNamespace())) {
                continue;
            }
            set.add(loc);
        }
        set.addAll(TaczCompat.allGunIds());
        List<ResourceLocation> list = new ArrayList<>(set);
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    static List<ResourceLocation> search(List<ResourceLocation> items, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return items;
        }
        List<ResourceLocation> filtered = new ArrayList<>();
        for (ResourceLocation loc : items) {
            ItemStack stack = ItemResolver.resolve(loc);
            String displayName = stack == null ? "" : stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (loc.getPath().contains(q) || displayName.contains(q)) {
                filtered.add(loc);
            }
        }
        return filtered;
    }

    private ItemCatalog() {}
}
