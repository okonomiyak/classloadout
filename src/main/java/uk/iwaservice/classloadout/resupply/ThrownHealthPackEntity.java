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

public class ThrownHealthPackEntity extends AbstractThrownResupplyEntity {

    public ThrownHealthPackEntity(EntityType<? extends ThrownHealthPackEntity> type, Level level) {
        super(type, level);
    }

    public ThrownHealthPackEntity(Level level, LivingEntity owner) {
        super(ModRegistry.THROWN_HEALTH_PACK.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModRegistry.THROWN_HEALTH_PACK_ITEM.get();
    }

    @Override
    protected void applyEffect(ServerPlayer player) {
        ResupplyEffects.heal(player, Config.THROW_PACK_HEALTH_PER_TICK.get());
    }

    @Override
    protected ParticleOptions impactParticle() {
        return ParticleTypes.HEART;
    }

    @Override
    protected String actionBarKey() {
        return "classloadout.hud.thrown_health_resupplying";
    }
}
