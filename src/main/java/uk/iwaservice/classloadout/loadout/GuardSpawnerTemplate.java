package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named, reusable guard spawner config (entity type, respawn delay, item list) an OP can save
 * from one {@code classloadout:guard_spawner} block and apply to any other via {@code
 * GuardSpawnerTemplateScreen} - lets several spawners share the same setup without retyping it
 * each time. Unlike a per-block config (keyed by {@link net.minecraft.core.GlobalPos} in {@link
 * LoadoutManager}), templates aren't tied to any position; applying one just copies its fields
 * onto whatever block position the command names. Mirrors {@link ClassDefinition}'s persistence
 * shape.
 */
public record GuardSpawnerTemplate(UUID id, String name, ResourceLocation entityType, int delaySeconds,
                                   List<ResourceLocation> items) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("EntityType", entityType.toString());
        tag.putInt("Delay", delaySeconds);
        ListTag itemList = new ListTag();
        for (ResourceLocation item : items) {
            itemList.add(StringTag.valueOf(item.toString()));
        }
        tag.put("Items", itemList);
        return tag;
    }

    public static GuardSpawnerTemplate load(CompoundTag tag) {
        List<ResourceLocation> items = new ArrayList<>();
        ListTag itemList = tag.getList("Items", Tag.TAG_STRING);
        for (Tag t : itemList) {
            items.add(new ResourceLocation(t.getAsString()));
        }
        return new GuardSpawnerTemplate(tag.getUUID("Id"), tag.getString("Name"),
                new ResourceLocation(tag.getString("EntityType")), tag.getInt("Delay"), items);
    }
}
