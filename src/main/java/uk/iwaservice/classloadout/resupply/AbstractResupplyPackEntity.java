package uk.iwaservice.classloadout.resupply;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import uk.iwaservice.classloadout.Config;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Shared lifecycle for a placed resupply pack (the stronger, right-click
 * counterpart to {@link AbstractThrownResupplyEntity}): no AI, stands where
 * placed, periodically scans nearby players for {@link #applyEffect}, and
 * self-discards after {@code packLifetimeSeconds}. Modeled directly on
 * squadtp's {@code RespawnBeaconEntity} - a {@link PathfinderMob} with
 * {@code noAi} is the established way in this codebase to get a "placed
 * object with combat-based destroy permission" without needing a Block at
 * all (no blockstate/model/loot-table plumbing, just an item texture).
 */
public abstract class AbstractResupplyPackEntity extends PathfinderMob {

    @Nullable
    private UUID ownerId;
    private int ageTicks;

    protected AbstractResupplyPackEntity(EntityType<? extends AbstractResupplyPackEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        // Intentionally no goals: it never moves or acts on its own.
    }

    /** Called once, right after spawning (before addFreshEntity), so onAddedToWorld below can register it. */
    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    @Nullable
    public UUID getOwner() {
        return ownerId;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && ownerId != null) {
            ResupplyPackRegistry.register(ownerId);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && ownerId != null) {
            ResupplyPackRegistry.unregister(ownerId);
        }
        super.remove(reason);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // don't die to fall damage right after being placed
    }

    /** friendlyOnlyDestroy: only the owner can land a hit that actually does anything. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (Config.FRIENDLY_ONLY_DESTROY.get() && ownerId != null
                && !(attacker instanceof ServerPlayer player && player.getUUID().equals(ownerId))) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ageTicks++;
        int lifetimeTicks = Config.PACK_LIFETIME_SECONDS.get() * 20;
        if (ageTicks >= lifetimeTicks) {
            discard();
            return;
        }

        int intervalTicks = Config.RESUPPLY_INTERVAL_SECONDS.get() * 20;
        if (intervalTicks <= 0 || ageTicks % intervalTicks != 0) {
            return;
        }
        int radius = Config.RESUPPLY_RADIUS.get();
        AABB area = new AABB(blockPosition()).inflate(radius);
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area)) {
            applyEffect(player);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("AgeTicks", ageTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            ownerId = tag.getUUID("Owner");
        }
        ageTicks = tag.getInt("AgeTicks");
    }

    protected abstract void applyEffect(ServerPlayer player);
}
