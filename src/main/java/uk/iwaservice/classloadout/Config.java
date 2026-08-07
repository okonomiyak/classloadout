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

    public static final ForgeConfigSpec.IntValue THROW_PACK_RADIUS;
    public static final ForgeConfigSpec.IntValue THROW_PACK_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue THROW_PACK_HEALTH_PER_TICK;
    public static final ForgeConfigSpec.IntValue THROW_PACK_AMMO_PER_TICK;
    public static final ForgeConfigSpec.IntValue THROW_PACK_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.IntValue THROW_COOLDOWN_SECONDS;

    public static final ForgeConfigSpec.IntValue BANDAGE_HEAL_AMOUNT;

    public static final ForgeConfigSpec.IntValue COVER_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_COVERS_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue COVER_MAX_HEALTH;

    public static final ForgeConfigSpec.BooleanValue CLEAR_INVENTORY_ON_DEATH;

    public static final ForgeConfigSpec.IntValue HAMMER_AOE_RADIUS;

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
                .defineInRange("packLifetimeSeconds", 20, 5, 3600);
        MAX_ACTIVE_PACKS_PER_PLAYER = b
                .comment("Maximum resupply packs a single player may have active at once. Shared between placed",
                        "packs and landed thrown packs (see the throwable section below) - they count together.")
                .defineInRange("maxActivePacksPerPlayer", 1, 1, 20);
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

        b.push("bandage");
        BANDAGE_HEAL_AMOUNT = b
                .comment("Health points (half-hearts) restored instantly by a bandage. Single-use - the item is",
                        "consumed on use (unless the player is in creative mode). Only usable while missing health.")
                .defineInRange("bandageHealAmount", 10, 1, 40);
        b.pop();

        b.push("cover");
        COVER_LIFETIME_SECONDS = b
                .comment("Seconds a placed cover (high-HP crouch barrier) lasts before it self-destructs.",
                        "Much longer than a resupply pack's lifetime by design - it's a defensive structure,",
                        "not a consumable.")
                .defineInRange("coverLifetimeSeconds", 600, 5, 7200);
        MAX_ACTIVE_COVERS_PER_PLAYER = b
                .comment("Maximum covers a single player may have active at once.")
                .defineInRange("maxActiveCoversPerPlayer", 2, 1, 20);
        COVER_MAX_HEALTH = b
                .comment("Max health of a placed cover. Applied per-instance when it's placed (read from this",
                        "config, not baked in at mod-load time), capped at 1024 - vanilla's own hard ceiling for",
                        "the max_health attribute, shared by every entity in the game.")
                .defineInRange("coverMaxHealth", 1000, 1, 1024);
        b.pop();

        b.push("death");
        CLEAR_INVENTORY_ON_DEATH = b
                .comment("If true, a player's entire inventory (main inventory, armor, offhand) is wiped on",
                        "respawn, except items on the OP-curated protected-items list (/class protect). Runs",
                        "before the personal loadout is re-equipped into hotbar slots 0-4, so loadout gear",
                        "always reappears regardless of this setting. Intended for use with the keepInventory",
                        "gamerule turned on - if it's off, vanilla already drops (and permanently loses) items,",
                        "protected or not, before this ever runs.")
                .define("clearInventoryOnDeath", true);
        b.pop();

        b.push("hammer");
        HAMMER_AOE_RADIUS = b
                .comment("Cube radius (in blocks) around a block broken with a SuperbWarfare hammer",
                        "(anything in the #forge:tools/hammer item tag) that also gets destroyed, like a",
                        "small explosion - but only for block types on the OP-curated list (/class hammerblocks).",
                        "The broken block itself must also be on that list, or no bonus blocks break at all.",
                        "0 disables the area effect entirely (still a normal single-block break).")
                .defineInRange("hammerAoeRadius", 1, 0, 4);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
