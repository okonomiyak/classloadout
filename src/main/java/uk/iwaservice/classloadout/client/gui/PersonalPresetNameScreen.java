package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Player-facing popup, opened from {@link LoadoutScreen}'s "Save Current as Preset" button, that
 * names and saves the player's current personal loadout as a new {@code /class mypreset} - capped
 * server-side at {@code LoadoutManager#MAX_PERSONAL_PRESETS}; a save past the cap just fails with
 * a chat message rather than being pre-checked here. Mirrors {@link PriceCountScreen}'s shape.
 */
public class PersonalPresetNameScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;


    private final Screen parent;

    private String nameValue = "";
    private EditBox nameBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public PersonalPresetNameScreen(Screen parent) {
        super(Component.translatable("classloadout.gui.mypreset_name_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(220, this.width - 16);
        panelHeight = Math.min(100, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD;
        nameBox = new EditBox(this.font, panelLeft + PAD, y, panelWidth - 2 * PAD, 18,
                Component.translatable("classloadout.gui.mypreset_name"));
        nameBox.setMaxLength(32);
        nameBox.setResponder(s -> nameValue = s);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        y += 18 + 8;

        int bw = (panelWidth - 2 * PAD - 4) / 2;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.save"), b -> save(), panelLeft + PAD, y, bw, 20));
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.cancel"), b -> minecraft.setScreen(parent), panelLeft + PAD + bw + 4, y, bw, 20));
    }

    private void save() {
        if (nameValue.isBlank()) {
            return;
        }
        command("class mypreset save " + nameValue);
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
