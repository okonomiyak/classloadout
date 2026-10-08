package uk.iwaservice.classloadout.loadout;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.ServerEvents;

import javax.annotation.Nullable;

/**
 * Keeps each player's per-team "standard kit" (see {@code /class teamkit} and
 * {@link LoadoutManager#getTeamKit}) in step with their vanilla scoreboard team: while on a team
 * with a kit, each kit slot is force-assigned (set + locked + equipped, same as {@code /class
 * forceassign}); on leaving, the slots go back to the player's previous item and lock state.
 */
public final class TeamKits {

    /**
     * Cheap when nothing changed (one map lookup); otherwise reverts the old team's kit and/or
     * applies the new one, then equips once. {@code refresh} names a team whose kit was just edited:
     * members already carrying that team's kit are reverted and re-applied from the new config.
     */
    public static void sync(MinecraftServer server, ServerPlayer player, @Nullable String refresh) {
        LoadoutManager manager = LoadoutManager.get(server);
        String team = player.getTeam() == null ? null : player.getTeam().getName();
        String applied = manager.getAppliedTeamKitTeam(player.getUUID());
        boolean leaving = applied != null && (!applied.equals(team) || applied.equals(refresh));
        boolean joining = team != null && !manager.getTeamKit(team).isEmpty() && (applied == null || leaving);
        if (!leaving && !joining) {
            return;
        }
        LoadoutManager.AppliedTeamKit reverted = leaving ? manager.revertTeamKit(server, player) : null;
        if (joining) {
            manager.applyTeamKit(server, player, team);
        }
        if (!joining && reverted != null && manager.getPersonalLoadout(player.getUUID()) == null) {
            // No loadout of their own: just take the kit's items back off rather than running the
            // full equip, which would clear the rest of their inventory.
            for (LoadoutSlot slot : reverted.previous().keySet()) {
                EquipmentSlot eq = slot.equipmentSlot();
                if (eq != null) {
                    player.setItemSlot(eq, ItemStack.EMPTY);
                } else {
                    player.getInventory().setItem(slot.hotbarIndex(), ItemStack.EMPTY);
                }
            }
        } else {
            ServerEvents.equipLoadout(player);
        }
        if (joining) {
            player.sendSystemMessage(Component.translatable("classloadout.msg.teamkit_notice", team));
        }
    }

    /** Called after an OP edits {@code team}'s kit. */
    public static void resync(MinecraftServer server, String team) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(server, player, team);
        }
    }

    private TeamKits() {}
}
