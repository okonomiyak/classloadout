package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import uk.iwaservice.classloadout.Config;

/**
 * Shared flight/landing lifecycle for the thrown health/ammo packs: ordinary
 * throwable-item flight (inherited from {@link ThrowableItemProjectile}, the
 * same base snowballs/eggs/ender pearls use) until the first impact, then it
 * settles in place and behaves like a short-lived, weaker version of
 * {@link AbstractResupplyPackBlockEntity} - periodic radius scan calling
 * {@link #applyEffect}, then self-discards after {@code throwPackLifetimeSeconds}.
 */
public abstract class AbstractThrownResupplyEntity extends ThrowableItemProjectile {

    private boolean landed;
    private int ticksSinceLanding;

    protected AbstractThrownResupplyEntity(EntityType<? extends AbstractThrownResupplyEntity> type, Level level) {
        super(type, level);
    }

    protected AbstractThrownResupplyEntity(EntityType<? extends AbstractThrownResupplyEntity> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    @Override
    protected void onHit(HitResult result) {
        if (landed || level().isClientSide) {
            return;
        }
        landed = true;
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(impactParticle(), getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        }
        var owner = getOwner();
        if (owner != null) {
            ResupplyPackRegistry.register(owner.getUUID());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !landed) {
            return;
        }
        ticksSinceLanding++;
        int lifetimeTicks = Config.THROW_PACK_LIFETIME_SECONDS.get() * 20;
        if (ticksSinceLanding >= lifetimeTicks) {
            discard();
            return;
        }

        int intervalTicks = Config.THROW_PACK_INTERVAL_SECONDS.get() * 20;
        if (intervalTicks <= 0 || ticksSinceLanding % intervalTicks != 0) {
            return;
        }
        int radius = Config.THROW_PACK_RADIUS.get();
        AABB area = new AABB(blockPosition()).inflate(radius);
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, area)) {
            applyEffect(player);
            player.displayClientMessage(Component.translatable(actionBarKey()), true);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        var owner = getOwner();
        if (!level().isClientSide && landed && owner != null) {
            ResupplyPackRegistry.unregister(owner.getUUID());
        }
        super.remove(reason);
    }

    protected abstract void applyEffect(ServerPlayer player);

    protected abstract ParticleOptions impactParticle();

    protected abstract String actionBarKey();
}
