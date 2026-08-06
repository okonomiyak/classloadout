package uk.iwaservice.classloadout.cover;

import net.minecraft.nbt.CompoundTag;
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
import uk.iwaservice.classloadout.Config;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A placeable, high-HP crouch barrier - a defensive counterpart to the
 * resupply packs ({@link uk.iwaservice.classloadout.resupply.AbstractResupplyPackEntity}):
 * same "no-AI {@link PathfinderMob}, no Block/BlockEntity/blockstate needed"
 * technique, but it does nothing on its own besides sit there and soak
 * damage. 1000 HP is a fixed design constant (not config-driven) because
 * {@link net.minecraftforge.event.entity.EntityAttributeCreationEvent} fires
 * before the server config is loaded, so {@link Config} values aren't
 * readable yet at attribute-registration time - see how the resupply packs'
 * own 1.0 HP is likewise hardcoded in {@code createAttributes()}.
 */
public class CoverEntity extends PathfinderMob {

    private static final double MAX_HEALTH = 1000.0;

    @Nullable
    private UUID ownerId;
    private int ageTicks;

    public CoverEntity(EntityType<? extends CoverEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
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
            CoverRegistry.register(ownerId);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && ownerId != null) {
            CoverRegistry.unregister(ownerId);
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
        if (level().isClientSide) {
            return;
        }
        ageTicks++;
        int lifetimeTicks = Config.COVER_LIFETIME_SECONDS.get() * 20;
        if (ageTicks >= lifetimeTicks) {
            discard();
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
}
