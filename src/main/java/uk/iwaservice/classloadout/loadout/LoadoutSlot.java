package uk.iwaservice.classloadout.loadout;

import javax.annotation.Nullable;

/** The five equipment categories a player (or a preset) can assign an item to. */
public enum LoadoutSlot {
    MAIN("main"),
    SIDEARM("sidearm"),
    THROWABLE("throwable"),
    GADGET("gadget"),
    MELEE("melee");

    private final String key;

    LoadoutSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Nullable
    public static LoadoutSlot byKey(String key) {
        for (LoadoutSlot slot : values()) {
            if (slot.key.equalsIgnoreCase(key)) {
                return slot;
            }
        }
        return null;
    }
}
