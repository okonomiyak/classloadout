package uk.iwaservice.classloadout.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The full pool of items an OP can browse when curating a slot whitelist or
 * editing a preset: every registered item in the {@code tacz},
 * {@code superbwarfare} and {@code minecraft} namespaces. Shared by
 * {@link ItemPickerScreen} and {@link WhitelistEditorScreen} so both grids
 * list and search the exact same candidates.
 */
final class ItemCatalog {

    private static final Set<String> ALLOWED_NAMESPACES = Set.of("tacz", "superbwarfare", "minecraft");

    static List<ResourceLocation> all() {
        List<ResourceLocation> list = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc == null || item == Items.AIR || !ALLOWED_NAMESPACES.contains(loc.getNamespace())) {
                continue;
            }
            list.add(loc);
        }
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
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            String displayName = item == null ? "" : new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
            if (loc.getPath().contains(q) || displayName.contains(q)) {
                filtered.add(loc);
            }
        }
        return filtered;
    }

    private ItemCatalog() {}
}
