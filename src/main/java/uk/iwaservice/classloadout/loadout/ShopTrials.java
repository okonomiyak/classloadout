package uk.iwaservice.classloadout.loadout;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.Config;

/**
 * Shop "trial" items: while a player is on one of {@code shop.trialTeams} (vanilla scoreboard
 * teams, e.g. a training range) they may take a free copy of an unpurchased shop item
 * ({@code /class trial}). Copies carry a custom-data marker and are swept out of the inventory
 * once the player is no longer on such a team.
 */
public final class ShopTrials {

    private static final String KEY = "classloadout_trial";

    public static void mark(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(KEY, true);
    }

    public static boolean isTrial(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(KEY);
    }

    public static boolean canTrial(ServerPlayer player) {
        return player.getTeam() != null && Config.SHOP_TRIAL_TEAMS.get().contains(player.getTeam().getName());
    }

    /** Called once a second per player: outside the trial teams, removes every marked stack (all slots incl. armor/offhand). */
    public static void sweep(ServerPlayer player) {
        if (canTrial(player)) {
            return;
        }
        Inventory inv = player.getInventory();
        boolean removed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isTrial(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        if (removed) {
            player.displayClientMessage(Component.translatable("classloadout.msg.trial_removed"), true);
        }
    }

    private ShopTrials() {}
}
