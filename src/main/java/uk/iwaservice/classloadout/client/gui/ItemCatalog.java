package uk.iwaservice.classloadout.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.compat.TaczCompat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The full pool of items an OP can browse when curating a slot whitelist,
 * editing a preset, or picking an ammo grant's ammo item: every registered
 * item in the {@code tacz}, {@code superbwarfare}, {@code minecraft} and
 * {@code classloadout} (the mod's own resupply pack items) namespaces, plus -
 * if TACZ is loaded - every individual TACZ gun id and every individual TACZ
 * ammo id (TACZ doesn't register one item per gun or per ammo type; see
 * {@link TaczCompat}), plus every OP-registered "exact held item" variant
 * (see {@link LoadoutClientData#getItemVariants()}). Shared by
 * {@link ItemPickerScreen} and {@link WhitelistEditorScreen} so both grids
 * list and search the exact same candidates.
 */
final class ItemCatalog {

    private static final Set<String> ALLOWED_NAMESPACES = Set.of("tacz", "superbwarfare", "minecraft", "classloadout");

    static List<ResourceLocation> all() {
        Set<ResourceLocation> set = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(item);
            if (loc == null || item == Items.AIR || !ALLOWED_NAMESPACES.contains(loc.getNamespace())) {
                continue;
            }
            set.add(loc);
        }
        set.addAll(TaczCompat.allGunIds());
        set.addAll(TaczCompat.allAmmoIds());
        set.addAll(LoadoutClientData.getItemVariants().keySet());
        List<ResourceLocation> list = new ArrayList<>(set);
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    /** Namespace-based grouping shown as tabs in {@link WhitelistEditorScreen}; OP-registered held-item variants get their own bucket regardless of the item they wrap, since browsing "guns" and browsing "the customized gun I registered" are different tasks. */
    enum Category { TACZ, SUPERBWARFARE, MINECRAFT, ALLOWED, HELD_ITEMS }

    /** Namespace bucket, or {@code null} for items that only appear under "All" (the mod's own pack items). {@link Category#ALLOWED} is not namespace-based - see {@link #byCategory}. */
    @Nullable
    static Category categoryOf(ResourceLocation loc) {
        if ("classloadout".equals(loc.getNamespace()) && loc.getPath().startsWith("variant_")) {
            return Category.HELD_ITEMS;
        }
        return switch (loc.getNamespace()) {
            case "tacz" -> Category.TACZ;
            case "superbwarfare" -> Category.SUPERBWARFARE;
            case "minecraft" -> Category.MINECRAFT;
            default -> null;
        };
    }

    /** Whitelisted under any slot; the "allowed" tab of screens that aren't tied to one slot. */
    static boolean allowedInAnySlot(ResourceLocation loc) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (LoadoutClientData.getWhitelist(slot).contains(loc)) {
                return true;
            }
        }
        return false;
    }

    /** {@code null} category means no filtering (all categories). {@code allowed} decides membership of {@link Category#ALLOWED}. */
    static List<ResourceLocation> byCategory(List<ResourceLocation> items, @Nullable Category category, Predicate<ResourceLocation> allowed) {
        if (category == null) {
            return items;
        }
        List<ResourceLocation> filtered = new ArrayList<>();
        for (ResourceLocation loc : items) {
            if (category == Category.ALLOWED ? allowed.test(loc) : categoryOf(loc) == category) {
                filtered.add(loc);
            }
        }
        return filtered;
    }

    static List<ResourceLocation> search(List<ResourceLocation> items, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return items;
        }
        List<ResourceLocation> filtered = new ArrayList<>();
        for (ResourceLocation loc : items) {
            ItemStack stack = ItemResolver.resolve(loc, LoadoutClientData.getItemVariants());
            String displayName = stack == null ? "" : stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (loc.getPath().contains(q) || displayName.contains(q)) {
                filtered.add(loc);
            }
        }
        return filtered;
    }

    private ItemCatalog() {}
}
