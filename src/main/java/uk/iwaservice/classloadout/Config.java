package uk.iwaservice.classloadout;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue RESUPPLY_RADIUS;
    public static final ForgeConfigSpec.IntValue RESUPPLY_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue RESUPPLY_HEALTH_PER_TICK;
    public static final ForgeConfigSpec.IntValue RESUPPLY_AMMO_PER_TICK;
    public static final ForgeConfigSpec.IntValue PACK_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_PACKS_PER_PLAYER;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_ONLY_DESTROY;

    public static final ForgeConfigSpec.IntValue THROW_PACK_RADIUS;
    public static final ForgeConfigSpec.IntValue THROW_PACK_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue THROW_PACK_HEALTH_PER_TICK;
    public static final ForgeConfigSpec.IntValue THROW_PACK_AMMO_PER_TICK;
    public static final ForgeConfigSpec.IntValue THROW_PACK_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.IntValue THROW_COOLDOWN_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("resupply");
        RESUPPLY_RADIUS = b
                .comment("Radius in blocks around a placed resupply pack within which players are affected.")
                .defineInRange("resupplyRadius", 4, 1, 32);
        RESUPPLY_INTERVAL_SECONDS = b
                .comment("Seconds between each resupply tick (heal/ammo application) while a pack is active.")
                .defineInRange("resupplyIntervalSeconds", 2, 1, 60);
        RESUPPLY_HEALTH_PER_TICK = b
                .comment("Health points restored per resupply tick by a health pack (1 point = half a heart).")
                .defineInRange("resupplyHealthPerTick", 1, 1, 20);
        RESUPPLY_AMMO_PER_TICK = b
                .comment("Ammo units restored per resupply tick by an ammo pack. Shared across weapon mods:",
                        "for TACZ this is the dummy-ammo amount added per tick; for SuperbWarfare it's the",
                        "amount added to each of the 5 ammo pools per tick.")
                .defineInRange("resupplyAmmoPerTick", 10, 1, 1000);
        PACK_LIFETIME_SECONDS = b
                .comment("Seconds a placed resupply pack lasts before it self-destructs.")
                .defineInRange("packLifetimeSeconds", 60, 5, 3600);
        MAX_ACTIVE_PACKS_PER_PLAYER = b
                .comment("Maximum resupply packs a single player may have active at once. Shared between placed",
                        "packs and landed thrown packs (see the throwable section below) - they count together.")
                .defineInRange("maxActivePacksPerPlayer", 1, 1, 20);
        FRIENDLY_ONLY_DESTROY = b
                .comment("If true, only the player who placed a resupply pack can break it.")
                .define("friendlyOnlyDestroy", true);
        b.pop();

        b.push("throwable");
        THROW_PACK_RADIUS = b
                .comment("Radius in blocks for a landed thrown pack. Deliberately smaller than resupplyRadius -",
                        "throwables are meant to be a weaker, more casual alternative to placing a pack.")
                .defineInRange("throwPackRadius", 2, 1, 32);
        THROW_PACK_INTERVAL_SECONDS = b
                .comment("Seconds between each resupply tick for a landed thrown pack.")
                .defineInRange("throwPackIntervalSeconds", 3, 1, 60);
        THROW_PACK_HEALTH_PER_TICK = b
                .comment("Health points restored per tick by a landed thrown health pack.")
                .defineInRange("throwPackHealthPerTick", 1, 1, 20);
        THROW_PACK_AMMO_PER_TICK = b
                .comment("Ammo units restored per tick by a landed thrown ammo pack (same units as resupplyAmmoPerTick).")
                .defineInRange("throwPackAmmoPerTick", 5, 1, 1000);
        THROW_PACK_LIFETIME_SECONDS = b
                .comment("Seconds a landed thrown pack lasts before it disappears.")
                .defineInRange("throwPackLifetimeSeconds", 25, 5, 3600);
        THROW_COOLDOWN_SECONDS = b
                .comment("Seconds before the same player can throw another pack. The item is not consumed on",
                        "throw - only this cooldown gates repeated use, matching vanilla's ender pearl cooldown.")
                .defineInRange("throwCooldownSeconds", 15, 0, 600);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
