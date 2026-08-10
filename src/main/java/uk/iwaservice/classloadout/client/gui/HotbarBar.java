package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A minimal stand-in for the vanilla hotbar (which Minecraft doesn't render
 * while any {@link net.minecraft.client.gui.screens.Screen} is open), drawn
 * at the bottom of the window by screens that offer an "Add Held Item"
 * button - so switching what's in hand to register as a variant doesn't
 * require closing the GUI first. Click a cell or press its vanilla hotbar
 * key (1-9) to select it, same as in-world.
 */
final class HotbarBar {
    private static final int SLOT = 20;
    private static final int SLOTS = 9;
    private static final int ICON = 16;
    private static final int MARGIN_BOTTOM = 22;

    static int width() {
        return SLOT * SLOTS;
    }

    static int top(Minecraft mc) {
        return mc.getWindow().getGuiScaledHeight() - MARGIN_BOTTOM;
    }

    static int left(Minecraft mc) {
        return (mc.getWindow().getGuiScaledWidth() - width()) / 2;
    }

    static void render(GuiGraphics graphics, Minecraft mc) {
        Player player = mc.player;
        if (player == null) {
            return;
        }
        int left = left(mc);
        int top = top(mc);
        int selected = player.getInventory().selected;
        for (int i = 0; i < SLOTS; i++) {
            int x = left + i * SLOT;
            graphics.fill(x, top, x + SLOT, top + SLOT, 0x90000000);
            graphics.renderOutline(x, top, SLOT, SLOT, i == selected ? 0xFFFFFFFF : 0xFF454A66);
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                int iconX = x + (SLOT - ICON) / 2;
                int iconY = top + (SLOT - ICON) / 2;
                graphics.renderItem(stack, iconX, iconY);
                graphics.renderItemDecorations(mc.font, stack, iconX, iconY);
            }
        }
    }

    /** Returns true (and switches the held item) if the click landed on a hotbar cell. */
    static boolean mouseClicked(Minecraft mc, double mouseX, double mouseY) {
        int left = left(mc);
        int top = top(mc);
        if (mouseX < left || mouseX >= left + width() || mouseY < top || mouseY >= top + SLOT) {
            return false;
        }
        selectSlot(mc, (int) ((mouseX - left) / SLOT));
        return true;
    }

    /** Returns true (and switches the held item) if {@code keyCode}/{@code scanCode} is one of the vanilla hotbar-select keys (1-9). */
    static boolean keyPressed(Minecraft mc, int keyCode, int scanCode) {
        for (int i = 0; i < SLOTS; i++) {
            if (mc.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                selectSlot(mc, i);
                return true;
            }
        }
        return false;
    }

    private static void selectSlot(Minecraft mc, int slot) {
        if (mc.player == null) {
            return;
        }
        mc.player.getInventory().selected = slot;
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private HotbarBar() {}
}
