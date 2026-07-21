package uk.iwaservice.classloadout.resupply;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import uk.iwaservice.classloadout.Config;

public class HealthPackEntity extends AbstractResupplyPackEntity {

    public HealthPackEntity(EntityType<? extends HealthPackEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void applyEffect(ServerPlayer player) {
        ResupplyEffects.heal(player, Config.RESUPPLY_HEALTH_PER_TICK.get());
    }
}
