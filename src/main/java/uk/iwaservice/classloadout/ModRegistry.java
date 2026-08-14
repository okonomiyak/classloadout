package uk.iwaservice.classloadout;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.classloadout.cover.CoverEntity;
import uk.iwaservice.classloadout.cover.CoverPlacerItem;
import uk.iwaservice.classloadout.resupply.AmmoPackEntity;
import uk.iwaservice.classloadout.resupply.BandageItem;
import uk.iwaservice.classloadout.resupply.HealthPackEntity;
import uk.iwaservice.classloadout.resupply.ResupplyPackPlacerItem;
import uk.iwaservice.classloadout.resupply.ThrowableResupplyItem;
import uk.iwaservice.classloadout.resupply.ThrownAmmoPackEntity;
import uk.iwaservice.classloadout.resupply.ThrownHealthPackEntity;

public final class ModRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ClassLoadoutMod.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ClassLoadoutMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ClassLoadoutMod.MODID);

    // --- loadout station (right-click to open the loadout screen without dying) ---

    /** Model is a desk shape (legs + tabletop, not a full cube) - noOcclusion() stops it from culling neighboring blocks' faces and from blocking light like a solid cube would; {@link LoadoutStationBlock} gives it a matching (non-full-cube) hitbox. */
    public static final RegistryObject<Block> LOADOUT_STATION = BLOCKS.register("loadout_station",
            () -> new LoadoutStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));
    public static final RegistryObject<Item> LOADOUT_STATION_ITEM = ITEMS.register("loadout_station",
            () -> new BlockItem(LOADOUT_STATION.get(), new Item.Properties()));

    // --- loadout locker (right-click to open the loadout screen, but changes only apply on next respawn) ---

    /** Model is a 16x14x16 box, not a full cube - noOcclusion() stops it from culling neighboring blocks' faces and from blocking light like a solid cube would; {@link LoadoutLockerBlock} gives it a matching (non-full-cube) hitbox. */
    public static final RegistryObject<Block> LOADOUT_LOCKER = BLOCKS.register("loadout_locker",
            () -> new LoadoutLockerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final RegistryObject<Item> LOADOUT_LOCKER_ITEM = ITEMS.register("loadout_locker",
            () -> new BlockItem(LOADOUT_LOCKER.get(), new Item.Properties()));

    // --- guard spawner (right-click to configure, OP only: entity type, respawn delay, spawned items) ---

    public static final RegistryObject<Block> GUARD_SPAWNER = BLOCKS.register("guard_spawner",
            () -> new GuardSpawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));
    public static final RegistryObject<Item> GUARD_SPAWNER_ITEM = ITEMS.register("guard_spawner",
            () -> new BlockItem(GUARD_SPAWNER.get(), new Item.Properties()));

    // --- placed resupply packs (right-click to place, stronger, combat-destroyable - see AbstractResupplyPackEntity) ---

    public static final RegistryObject<EntityType<HealthPackEntity>> HEALTH_PACK = ENTITY_TYPES.register(
            "health_pack",
            () -> EntityType.Builder.<HealthPackEntity>of(HealthPackEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("health_pack"));
    public static final RegistryObject<Item> HEALTH_PACK_ITEM = ITEMS.register("health_pack",
            () -> new ResupplyPackPlacerItem(new Item.Properties(), HEALTH_PACK));

    public static final RegistryObject<EntityType<AmmoPackEntity>> AMMO_PACK = ENTITY_TYPES.register(
            "ammo_pack",
            () -> EntityType.Builder.<AmmoPackEntity>of(AmmoPackEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("ammo_pack"));
    public static final RegistryObject<Item> AMMO_PACK_ITEM = ITEMS.register("ammo_pack",
            () -> new ResupplyPackPlacerItem(new Item.Properties(), AMMO_PACK));

    // --- thrown resupply packs (snowball-style, weaker - see AbstractThrownResupplyEntity) ---

    public static final RegistryObject<EntityType<ThrownHealthPackEntity>> THROWN_HEALTH_PACK = ENTITY_TYPES.register(
            "thrown_health_pack",
            () -> EntityType.Builder.<ThrownHealthPackEntity>of(ThrownHealthPackEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_health_pack"));
    public static final RegistryObject<Item> THROWN_HEALTH_PACK_ITEM = ITEMS.register("thrown_health_pack",
            () -> new ThrowableResupplyItem(new Item.Properties().stacksTo(1),
                    (level, player) -> new ThrownHealthPackEntity(level, player)));

    public static final RegistryObject<EntityType<ThrownAmmoPackEntity>> THROWN_AMMO_PACK = ENTITY_TYPES.register(
            "thrown_ammo_pack",
            () -> EntityType.Builder.<ThrownAmmoPackEntity>of(ThrownAmmoPackEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_ammo_pack"));
    public static final RegistryObject<Item> THROWN_AMMO_PACK_ITEM = ITEMS.register("thrown_ammo_pack",
            () -> new ThrowableResupplyItem(new Item.Properties().stacksTo(1),
                    (level, player) -> new ThrownAmmoPackEntity(level, player)));

    // --- placed cover (right-click to place, high-HP crouch barrier - see CoverEntity) ---

    public static final RegistryObject<EntityType<CoverEntity>> COVER = ENTITY_TYPES.register(
            "cover",
            () -> EntityType.Builder.<CoverEntity>of(CoverEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.25f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("cover"));
    public static final RegistryObject<Item> COVER_ITEM = ITEMS.register("cover",
            () -> new CoverPlacerItem(new Item.Properties()));

    // --- self-heal item (right-click to instantly heal yourself, consumed on use) ---

    public static final RegistryObject<Item> BANDAGE_ITEM = ITEMS.register("bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITY_TYPES.register(modBus);
    }

    private ModRegistry() {}
}
