package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Vanilla Button logic with the card look (dark fill, thin outline, white when hovered) instead of the stone texture; also holds the style shared by the bar-style screens. */
final class FlatButton extends Button {
    static final int COLOR_TEXT = 0xFFFFFFFF;
    static final int COLOR_TEXT_DIM = 0xFF9AA0A8;
    static final int COLOR_CARD_BG = 0xB0101418;
    static final int COLOR_CARD_LINE = 0x50FFFFFF;

    FlatButton(Component label, OnPress onPress, int x, int y, int w, int h) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawCardFrame(graphics, getX(), getY(), width, height, isHoveredOrFocused());
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                getY() + (height - 8) / 2, COLOR_TEXT);
    }

    /** Dark translucent fill with a 1px outline, white when {@code hover}. */
    static void drawCardFrame(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, COLOR_CARD_BG);
        g.renderOutline(x, y, w, h, hover ? COLOR_TEXT : COLOR_CARD_LINE);
    }

    /** Short corner brackets around the rectangle. */
    /**
     * Left margin for a bottom bar: Item Zoomer draws its zoomed item centred in the space left of
     * the reported panel rect (and only when that space is at least ~42px), so give the bar's spare
     * width up to that side, capped at a third of the screen (at least 125, Item Zoomer's full-size zoom), never squeezing the bar
     * below {@code minBarWidth}.
     */
    static int zoomMargin(int screenWidth, int minBarWidth) {
        return Math.max(8, Math.min(Math.max(125, screenWidth / 3), screenWidth - minBarWidth - 8));
    }

    static void drawBrackets(GuiGraphics g, int x, int y, int w, int h) {
        corner(g, x, y, 1, 1);
        corner(g, x + w, y, -1, 1);
        corner(g, x, y + h, 1, -1);
        corner(g, x + w, y + h, -1, -1);
    }

    /** Short 1px L-shaped bracket at a bar corner; dx/dy point into the bar. */
    private static void corner(GuiGraphics g, int x, int y, int dx, int dy) {
        int len = 10;
        int x2 = dx > 0 ? x + len : x - len;
        int y2 = dy > 0 ? y + len : y - len;
        g.fill(Math.min(x, x2), dy > 0 ? y : y - 1, Math.max(x, x2), dy > 0 ? y + 1 : y, 0x80FFFFFF);
        g.fill(dx > 0 ? x : x - 1, Math.min(y, y2), dx > 0 ? x + 1 : x, Math.max(y, y2), 0x80FFFFFF);
    }
}
