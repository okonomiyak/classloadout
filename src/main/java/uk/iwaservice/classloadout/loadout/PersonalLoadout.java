package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A player's own, freely self-assigned equipment loadout: one item per
 * {@link LoadoutSlot}. This - not any preset - is what actually gets
 * equipped on respawn; applying a preset just copies its five items in here
 * as a starting point the player can keep tweaking slot by slot.
 */
public record PersonalLoadout(@Nullable ResourceLocation main,
                              @Nullable ResourceLocation sidearm,
                              @Nullable ResourceLocation throwable,
                              @Nullable ResourceLocation gadget,
                              @Nullable ResourceLocation melee) {

    public static final PersonalLoadout EMPTY = new PersonalLoadout(null, null, null, null, null);

    @Nullable
    public ResourceLocation get(LoadoutSlot slot) {
        return switch (slot) {
            case MAIN -> main;
            case SIDEARM -> sidearm;
            case THROWABLE -> throwable;
            case GADGET -> gadget;
            case MELEE -> melee;
        };
    }

    public PersonalLoadout withSlot(LoadoutSlot slot, @Nullable ResourceLocation item) {
        return switch (slot) {
            case MAIN -> new PersonalLoadout(item, sidearm, throwable, gadget, melee);
            case SIDEARM -> new PersonalLoadout(main, item, throwable, gadget, melee);
            case THROWABLE -> new PersonalLoadout(main, sidearm, item, gadget, melee);
            case GADGET -> new PersonalLoadout(main, sidearm, throwable, item, melee);
            case MELEE -> new PersonalLoadout(main, sidearm, throwable, gadget, item);
        };
    }

    public static PersonalLoadout fromClass(ClassDefinition def) {
        return new PersonalLoadout(def.main(), def.sidearm(), def.throwable(), def.gadget(), def.melee());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        putIfPresent(tag, "Main", main);
        putIfPresent(tag, "Sidearm", sidearm);
        putIfPresent(tag, "Throwable", throwable);
        putIfPresent(tag, "Gadget", gadget);
        putIfPresent(tag, "Melee", melee);
        return tag;
    }

    public static PersonalLoadout load(CompoundTag tag) {
        return new PersonalLoadout(readIfPresent(tag, "Main"), readIfPresent(tag, "Sidearm"),
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
