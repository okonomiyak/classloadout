package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A single loadout class: display name, icon and up to five equipment slots.
 * Slot resource locations are the sole persisted reference to an item - if
 * the owning mod is later removed, {@link uk.iwaservice.classloadout.ServerEvents}
 * and the client GUIs simply skip the missing entry.
 */
public record ClassDefinition(UUID id, String name,
                              @Nullable ResourceLocation icon,
                              @Nullable ResourceLocation main,
                              @Nullable ResourceLocation sidearm,
                              @Nullable ResourceLocation throwable,
                              @Nullable ResourceLocation gadget,
                              @Nullable ResourceLocation melee) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        putIfPresent(tag, "Icon", icon);
        putIfPresent(tag, "Main", main);
        putIfPresent(tag, "Sidearm", sidearm);
        putIfPresent(tag, "Throwable", throwable);
        putIfPresent(tag, "Gadget", gadget);
        putIfPresent(tag, "Melee", melee);
        return tag;
    }

    public static ClassDefinition load(CompoundTag tag) {
        return new ClassDefinition(tag.getUUID("Id"), tag.getString("Name"),
                readIfPresent(tag, "Icon"), readIfPresent(tag, "Main"), readIfPresent(tag, "Sidearm"),
                readIfPresent(tag, "Throwable"), readIfPresent(tag, "Gadget"), readIfPresent(tag, "Melee"));
    }

    private static void putIfPresent(CompoundTag tag, String key, @Nullable ResourceLocation loc) {
        if (loc != null) {
            tag.putString(key, loc.toString());
        }
    }

    @Nullable
    private static ResourceLocation readIfPresent(CompoundTag tag, String key) {
        return tag.contains(key) ? new ResourceLocation(tag.getString(key)) : null;
    }
}
