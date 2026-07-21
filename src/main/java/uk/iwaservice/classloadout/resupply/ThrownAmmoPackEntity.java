package uk.iwaservice.classloadout.resupply;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import uk.iwaservice.classloadout.Config;
import uk.iwaservice.classloadout.ModRegistry;

public class ThrownAmmoPackEntity extends AbstractThrownResupplyEntity {

    public ThrownAmmoPackEntity(EntityType<? extends ThrownAmmoPackEntity> type, Level level) {
        super(type, level);
    }

    public ThrownAmmoPackEntity(Level level, LivingEntity owner) {
        super(ModRegistry.THROWN_AMMO_PACK.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModRegistry.THROWN_AMMO_PACK_ITEM.get();
    }

    @Override
    protected void applyEffect(ServerPlayer player) {
        ResupplyEffects.resupplyAmmo(player, Config.THROW_PACK_AMMO_PER_TICK.get());
    }

    @Override
    protected ParticleOptions impactParticle() {
        return ParticleTypes.CRIT;
    }

    @Override
    protected String actionBarKey() {
        return "classloadout.hud.thrown_ammo_resupplying";
    }
}
