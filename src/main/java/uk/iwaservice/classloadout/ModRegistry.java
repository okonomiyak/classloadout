package uk.iwaservice.classloadout;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.classloadout.resupply.AmmoPackBlock;
import uk.iwaservice.classloadout.resupply.AmmoPackBlockEntity;
import uk.iwaservice.classloadout.resupply.HealthPackBlock;
import uk.iwaservice.classloadout.resupply.HealthPackBlockEntity;
import uk.iwaservice.classloadout.resupply.ResupplyPackItem;
import uk.iwaservice.classloadout.resupply.ThrowableResupplyItem;
import uk.iwaservice.classloadout.resupply.ThrownAmmoPackEntity;
import uk.iwaservice.classloadout.resupply.ThrownHealthPackEntity;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ClassLoadoutMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ClassLoadoutMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ClassLoadoutMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ClassLoadoutMod.MODID);

    public static final RegistryObject<Block> HEALTH_PACK = BLOCKS.register("health_pack",
            () -> new HealthPackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(0.5f).noOcclusion()));
    public static final RegistryObject<Item> HEALTH_PACK_ITEM = ITEMS.register("health_pack",
            () -> new ResupplyPackItem(HEALTH_PACK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<HealthPackBlockEntity>> HEALTH_PACK_BE =
            BLOCK_ENTITIES.register("health_pack",
                    () -> BlockEntityType.Builder.of(HealthPackBlockEntity::new, HEALTH_PACK.get()).build(null));

    public static final RegistryObject<Block> AMMO_PACK = BLOCKS.register("ammo_pack",
            () -> new AmmoPackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5f).noOcclusion()));
    public static final RegistryObject<Item> AMMO_PACK_ITEM = ITEMS.register("ammo_pack",
            () -> new ResupplyPackItem(AMMO_PACK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<AmmoPackBlockEntity>> AMMO_PACK_BE =
            BLOCK_ENTITIES.register("ammo_pack",
                    () -> BlockEntityType.Builder.of(AmmoPackBlockEntity::new, AMMO_PACK.get()).build(null));

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

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
    }

    private ModRegistry() {}
}
