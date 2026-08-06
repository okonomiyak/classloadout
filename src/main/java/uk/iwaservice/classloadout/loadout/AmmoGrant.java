package uk.iwaservice.classloadout.loadout;

import net.minecraft.resources.ResourceLocation;

/**
 * OP-configured extra: when a whitelisted (slot, item) pair has one of
 * these attached, equipping that item on respawn also gives the player
 * {@code count} of {@code ammoItem} into their general inventory (not a
 * hotbar slot). Optional - most whitelist entries have none.
 */
public record AmmoGrant(ResourceLocation ammoItem, int count) {
}
