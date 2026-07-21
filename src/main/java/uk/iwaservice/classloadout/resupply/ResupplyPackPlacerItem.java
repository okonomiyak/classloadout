package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.classloadout.Config;

/**
 * Shared {@link Item} for both placed resupply packs. Right-click a block
 * face to spawn the entity on top of it, exactly like squadtp's
 * {@code RespawnBeaconItem} - no Block/BlockEntity/blockstate/model needed,
 * just the item's own icon (see {@link AbstractResupplyPackEntity}).
 *
 * <p>Takes the {@link RegistryObject} rather than a resolved {@link EntityType}:
 * items and entity types are registered via separate {@code DeferredRegister}s
 * with no ordering guarantee between them, so resolving the entity type at
 * construction time (during the items' own registration) can run before the
 * entity type is bound and crash. Resolving lazily inside {@link #useOn} -
 * long after all registries have settled - is always safe.
 */
public class ResupplyPackPlacerItem extends Item {

    private final RegistryObject<? extends EntityType<? extends AbstractResupplyPackEntity>> entityType;

    public ResupplyPackPlacerItem(Properties properties, RegistryObject<? extends EntityType<? extends AbstractResupplyPackEntity>> entityType) {
        super(properties);
        this.entityType = entityType;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (ResupplyPackRegistry.countActive(player.getUUID()) >= Config.MAX_ACTIVE_PACKS_PER_PLAYER.get()) {
            player.sendSystemMessage(Component.translatable("classloadout.msg.pack_limit_reached"));
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        AbstractResupplyPackEntity pack = entityType.get().create(level);
        if (pack == null) {
            return InteractionResult.FAIL;
        }
        pack.setOwner(player.getUUID());
        pack.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        if (!level.addFreshEntity(pack)) {
            return InteractionResult.FAIL;
        }

        context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
