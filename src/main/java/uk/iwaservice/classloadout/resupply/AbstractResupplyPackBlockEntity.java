package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import uk.iwaservice.classloadout.Config;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Shared lifecycle for a placed resupply pack: owner tracking (for
 * {@code maxActivePacksPerPlayer} and {@code friendlyOnlyDestroy}), a
 * self-destruct timer, and a periodic scan of nearby players that hands off
 * to {@link #applyEffect} for the health/ammo pack subclasses to specialize.
 */
public abstract class AbstractResupplyPackBlockEntity extends BlockEntity {

    @Nullable
    private UUID ownerId;
    private int ageTicks;

    protected AbstractResupplyPackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Called once, right after placement (see {@code ResupplyPackItem}); registers with the active-count tracker. */
    public void setOwner(UUID owner) {
        this.ownerId = owner;
        setChanged();
        if (level instanceof ServerLevel) {
            ResupplyPackRegistry.register(owner);
        }
    }

    @Nullable
    public UUID getOwner() {
        return ownerId;
    }

    /** Re-registers on world/chunk load if this pack already has an owner from NBT (placement itself registers via setOwner). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && ownerId != null) {
            ResupplyPackRegistry.register(ownerId);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && ownerId != null) {
            ResupplyPackRegistry.unregister(ownerId);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide && ownerId != null) {
            ResupplyPackRegistry.unregister(ownerId);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("Owner")) {
            ownerId = tag.getUUID("Owner");
        }
        ageTicks = tag.getInt("AgeTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("AgeTicks", ageTicks);
    }

    public void tick(ServerLevel level) {
        ageTicks++;
        int lifetimeTicks = Config.PACK_LIFETIME_SECONDS.get() * 20;
        if (ageTicks >= lifetimeTicks) {
            level.removeBlock(worldPosition, false);
            return;
        }

        int intervalTicks = Config.RESUPPLY_INTERVAL_SECONDS.get() * 20;
        if (intervalTicks <= 0 || ageTicks % intervalTicks != 0) {
            return;
        }
        int radius = Config.RESUPPLY_RADIUS.get();
        AABB area = new AABB(worldPosition).inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            applyEffect(player);
        }
    }

    protected abstract void applyEffect(ServerPlayer player);
}
