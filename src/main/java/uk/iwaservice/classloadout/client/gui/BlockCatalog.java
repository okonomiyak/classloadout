package uk.iwaservice.classloadout.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The full pool of block types an OP can browse when curating the hammer
 * AOE block whitelist ({@link HammerBlocksEditorScreen}) - every registered
 * block (any namespace, unlike {@link ItemCatalog}'s curated set, since this
 * is about arbitrary terrain/building blocks, not gear) that has a usable
 * item form to render as an icon. Blocks without one (e.g. purely
 * technical/fluid blocks) are skipped since they can't be picked from a
 * grid meaningfully.
 */
final class BlockCatalog {

    static List<ResourceLocation> all() {
        List<ResourceLocation> list = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation loc = ForgeRegistries.BLOCKS.getKey(block);
            if (loc == null || block.asItem() == Items.AIR) {
                continue;
            }
            list.add(loc);
        }
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    static List<ResourceLocation> search(List<ResourceLocation> blocks, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return blocks;
        }
        List<ResourceLocation> filtered = new ArrayList<>();
        for (ResourceLocation loc : blocks) {
            if (loc.getPath().contains(q)) {
                filtered.add(loc);
            }
        }
        return filtered;
    }

    private BlockCatalog() {}
}
