package uk.iwaservice.classloadout;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.classloadout.resupply.AmmoPackEntity;
import uk.iwaservice.classloadout.resupply.HealthPackEntity;
import uk.iwaservice.classloadout.resupply.ResupplyPackPlacerItem;
import uk.iwaservice.classloadout.resupply.ThrowableResupplyItem;
import uk.iwaservice.classloadout.resupply.ThrownAmmoPackEntity;
import uk.iwaservice.classloadout.resupply.ThrownHealthPackEntity;

public final class ModRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ClassLoadoutMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ClassLoadoutMod.MODID);

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

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
    }

    private ModRegistry() {}
}
