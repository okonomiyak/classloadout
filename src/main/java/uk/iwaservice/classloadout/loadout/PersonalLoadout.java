package uk.iwaservice.classloadout.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A player's own, freely self-assigned equipment loadout: one item per
 * {@link LoadoutSlot} (six hotbar gear slots plus four armor slots). This -
 * not any preset - is what actually gets equipped on respawn; applying a
 * preset just copies its ten items in here as a starting point the player
 * can keep tweaking slot by slot.
 */
public record PersonalLoadout(@Nullable ResourceLocation main,
                              @Nullable ResourceLocation sidearm,
                              @Nullable ResourceLocation throwable,
                              @Nullable ResourceLocation gadget,
                              @Nullable ResourceLocation gadget2,
                              @Nullable ResourceLocation melee,
                              @Nullable ResourceLocation helmet,
                              @Nullable ResourceLocation chestplate,
                              @Nullable ResourceLocation leggings,
                              @Nullable ResourceLocation boots) {

    public static final PersonalLoadout EMPTY =
            new PersonalLoadout(null, null, null, null, null, null, null, null, null, null);

    @Nullable
    public ResourceLocation get(LoadoutSlot slot) {
        return switch (slot) {
            case MAIN -> main;
            case SIDEARM -> sidearm;
            case THROWABLE -> throwable;
            case GADGET -> gadget;
            case GADGET2 -> gadget2;
            case MELEE -> melee;
            case HELMET -> helmet;
            case CHESTPLATE -> chestplate;
            case LEGGINGS -> leggings;
            case BOOTS -> boots;
        };
    }

    public PersonalLoadout withSlot(LoadoutSlot slot, @Nullable ResourceLocation item) {
        return switch (slot) {
            case MAIN -> new PersonalLoadout(item, sidearm, throwable, gadget, gadget2, melee, helmet, chestplate, leggings, boots);
            case SIDEARM -> new PersonalLoadout(main, item, throwable, gadget, gadget2, melee, helmet, chestplate, leggings, boots);
            case THROWABLE -> new PersonalLoadout(main, sidearm, item, gadget, gadget2, melee, helmet, chestplate, leggings, boots);
            case GADGET -> new PersonalLoadout(main, sidearm, throwable, item, gadget2, melee, helmet, chestplate, leggings, boots);
            case GADGET2 -> new PersonalLoadout(main, sidearm, throwable, gadget, item, melee, helmet, chestplate, leggings, boots);
            case MELEE -> new PersonalLoadout(main, sidearm, throwable, gadget, gadget2, item, helmet, chestplate, leggings, boots);
            case HELMET -> new PersonalLoadout(main, sidearm, throwable, gadget, gadget2, melee, item, chestplate, leggings, boots);
            case CHESTPLATE -> new PersonalLoadout(main, sidearm, throwable, gadget, gadget2, melee, helmet, item, leggings, boots);
            case LEGGINGS -> new PersonalLoadout(main, sidearm, throwable, gadget, gadget2, melee, helmet, chestplate, item, boots);
            case BOOTS -> new PersonalLoadout(main, sidearm, throwable, gadget, gadget2, melee, helmet, chestplate, leggings, item);
        };
    }

    public static PersonalLoadout fromClass(ClassDefinition def) {
        return new PersonalLoadout(def.main(), def.sidearm(), def.throwable(), def.gadget(), def.gadget2(), def.melee(),
                def.helmet(), def.chestplate(), def.leggings(), def.boots());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
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

    public static PersonalLoadout load(CompoundTag tag) {
        return new PersonalLoadout(readIfPresent(tag, "Main"), readIfPresent(tag, "Sidearm"),
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
