package uk.iwaservice.classloadout.loadout;

import net.minecraft.world.entity.EquipmentSlot;

import javax.annotation.Nullable;

/**
 * The ten equipment categories a player (or a preset) can assign an item
 * to: six hotbar-equipped gear slots (positioned into hotbar slots 0-5, in
 * this enum's declared order - see {@link #hotbarIndex()}) plus the four
 * armor slots (equipped via {@link #equipmentSlot()} instead, not the
 * hotbar). Exactly one of {@link #hotbarIndex()} (non-negative) or {@link
 * #equipmentSlot()} (non-null) applies to any given slot, never both.
 */
public enum LoadoutSlot {
    MAIN("main", 0, null),
    SIDEARM("sidearm", 1, null),
    THROWABLE("throwable", 2, null),
    GADGET("gadget", 3, null),
    GADGET2("gadget2", 4, null),
    MELEE("melee", 5, null),
    HELMET("helmet", -1, EquipmentSlot.HEAD),
    CHESTPLATE("chestplate", -1, EquipmentSlot.CHEST),
    LEGGINGS("leggings", -1, EquipmentSlot.LEGS),
    BOOTS("boots", -1, EquipmentSlot.FEET);

    private final String key;
    private final int hotbarIndex;
    @Nullable
    private final EquipmentSlot equipmentSlot;

    LoadoutSlot(String key, int hotbarIndex, @Nullable EquipmentSlot equipmentSlot) {
        this.key = key;
        this.hotbarIndex = hotbarIndex;
        this.equipmentSlot = equipmentSlot;
    }

    public String key() {
        return key;
    }

    /** -1 for armor slots - see {@link #equipmentSlot()} instead. */
    public int hotbarIndex() {
        return hotbarIndex;
    }

    /** Null for hotbar-equipped gear slots - see {@link #hotbarIndex()} instead. */
    @Nullable
    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
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
