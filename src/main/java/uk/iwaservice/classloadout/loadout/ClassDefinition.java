package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A single loadout class: display name, icon and up to ten equipment slots
 * (six hotbar gear slots plus four armor slots). Slot resource locations
 * are the sole persisted reference to an item - if the owning mod is later
 * removed, {@link uk.iwaservice.classloadout.ServerEvents} and the client
 * GUIs simply skip the missing entry.
 */
public record ClassDefinition(UUID id, String name,
                              @Nullable ResourceLocation icon,
                              @Nullable ResourceLocation main,
                              @Nullable ResourceLocation sidearm,
                              @Nullable ResourceLocation throwable,
                              @Nullable ResourceLocation gadget,
                              @Nullable ResourceLocation gadget2,
                              @Nullable ResourceLocation melee,
                              @Nullable ResourceLocation helmet,
                              @Nullable ResourceLocation chestplate,
                              @Nullable ResourceLocation leggings,
                              @Nullable ResourceLocation boots) {

    public ClassDefinition withName(String newName) {
        return new ClassDefinition(id, newName, icon, main, sidearm, throwable, gadget, gadget2, melee,
                helmet, chestplate, leggings, boots);
    }

    public ClassDefinition withIcon(@Nullable ResourceLocation newIcon) {
        return new ClassDefinition(id, name, newIcon, main, sidearm, throwable, gadget, gadget2, melee,
                helmet, chestplate, leggings, boots);
    }

    public ClassDefinition withSlot(LoadoutSlot slot, @Nullable ResourceLocation item) {
        return switch (slot) {
            case MAIN -> new ClassDefinition(id, name, icon, item, sidearm, throwable, gadget, gadget2, melee, helmet, chestplate, leggings, boots);
            case SIDEARM -> new ClassDefinition(id, name, icon, main, item, throwable, gadget, gadget2, melee, helmet, chestplate, leggings, boots);
            case THROWABLE -> new ClassDefinition(id, name, icon, main, sidearm, item, gadget, gadget2, melee, helmet, chestplate, leggings, boots);
            case GADGET -> new ClassDefinition(id, name, icon, main, sidearm, throwable, item, gadget2, melee, helmet, chestplate, leggings, boots);
            case GADGET2 -> new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, item, melee, helmet, chestplate, leggings, boots);
            case MELEE -> new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, gadget2, item, helmet, chestplate, leggings, boots);
            case HELMET -> new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, gadget2, melee, item, chestplate, leggings, boots);
            case CHESTPLATE -> new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, gadget2, melee, helmet, item, leggings, boots);
            case LEGGINGS -> new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, gadget2, melee, helmet, chestplate, item, boots);
            case BOOTS -> new ClassDefinition(id, name, icon, main, sidearm, throwable, gadget, gadget2, melee, helmet, chestplate, leggings, item);
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        putIfPresent(tag, "Icon", icon);
        putIfPresent(tag, "Main", main);
        putIfPresent(tag, "Sidearm", sidearm);
        putIfPresent(tag, "Throwable", throwable);
        putIfPresent(tag, "Gadget", gadget);
        putIfPresent(tag, "Gadget2", gadget2);
        putIfPresent(tag, "Melee", melee);
        putIfPresent(tag, "Helmet", helmet);
        putIfPresent(tag, "Chestplate", chestplate);
        putIfPresent(tag, "Leggings", leggings);
        putIfPresent(tag, "Boots", boots);
        return tag;
    }

    public static ClassDefinition load(CompoundTag tag) {
        return new ClassDefinition(tag.getUUID("Id"), tag.getString("Name"),
                readIfPresent(tag, "Icon"), readIfPresent(tag, "Main"), readIfPresent(tag, "Sidearm"),
                readIfPresent(tag, "Throwable"), readIfPresent(tag, "Gadget"), readIfPresent(tag, "Gadget2"),
                readIfPresent(tag, "Melee"), readIfPresent(tag, "Helmet"), readIfPresent(tag, "Chestplate"),
                readIfPresent(tag, "Leggings"), readIfPresent(tag, "Boots"));
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
