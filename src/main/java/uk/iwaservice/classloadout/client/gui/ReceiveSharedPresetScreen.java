package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Player-facing popup, opened from {@link LoadoutScreen}'s "Receive Preset" button, for pasting
 * in someone else's preset code (see {@code LoadoutScreen#showCode}) to redeem into the player's
 * own shared-preset slot via {@code /class mypreset receive <code>}. No client-side validation
 * that the pasted text is even a UUID - an invalid one just gets rejected by the server's own
 * command parsing, same as typing the command by hand would. Mirrors {@link PersonalPresetNameScreen}.
 */
public class ReceiveSharedPresetScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;


    private final Screen parent;

    private String codeValue = "";
    private EditBox codeBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public ReceiveSharedPresetScreen(Screen parent) {
        super(Component.translatable("classloadout.gui.mypreset_receive_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(260, this.width - 16);
        panelHeight = Math.min(100, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD;
        codeBox = new EditBox(this.font, panelLeft + PAD, y, panelWidth - 2 * PAD, 18,
                Component.translatable("classloadout.gui.mypreset_receive_code"));
        codeBox.setMaxLength(36);
        codeBox.setResponder(s -> codeValue = s);
        addRenderableWidget(codeBox);
        setInitialFocus(codeBox);
        y += 18 + 8;

        int bw = (panelWidth - 2 * PAD - 4) / 2;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.mypreset_receive"), b -> receive(), panelLeft + PAD, y, bw, 20));
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.cancel"), b -> minecraft.setScreen(parent), panelLeft + PAD + bw + 4, y, bw, 20));
    }

    private void receive() {
        if (codeValue.isBlank()) {
            return;
        }
        command("class mypreset receive " + codeValue.trim());
        minecraft.setScreen(parent);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        FlatButton.drawPanel(graphics, l, t, panelWidth, panelHeight);
        graphics.fill(l, t + HEADER_H, r, t + HEADER_H + 1, FlatButton.COLOR_CARD_LINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, FlatButton.COLOR_TEXT_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
