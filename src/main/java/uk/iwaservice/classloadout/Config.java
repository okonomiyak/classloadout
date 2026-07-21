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
                .comment("Maximum resupply packs (health + ammo combined) a single player may have active at once.")
                .defineInRange("maxActivePacksPerPlayer", 1, 1, 20);
        FRIENDLY_ONLY_DESTROY = b
                .comment("If true, only the player who placed a resupply pack can break it.")
                .define("friendlyOnlyDestroy", true);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
