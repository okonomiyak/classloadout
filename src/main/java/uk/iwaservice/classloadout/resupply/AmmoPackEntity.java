package uk.iwaservice.classloadout.resupply;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import uk.iwaservice.classloadout.Config;

public class AmmoPackEntity extends AbstractResupplyPackEntity {

    public AmmoPackEntity(EntityType<? extends AmmoPackEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void applyEffect(ServerPlayer player) {
        ResupplyEffects.resupplyAmmo(player, Config.RESUPPLY_AMMO_PER_TICK.get());
    }
}
