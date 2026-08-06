package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.ItemResolver;
import uk.iwaservice.classloadout.client.LoadoutClientData;

/**
 * OP-only popup, opened by right-clicking a cell in {@link SpawnKitEditorScreen},
 * that sets the exact count for that item's spawn kit entry. Mutates
 * through the same {@code /class spawnkit add/remove} command surface as
 * everything else - no C2S packets. Simpler than {@link AmmoGrantScreen}:
 * there's only one item involved (the grid cell itself), not a separate
 * item-to-grant picker.
 */
public class SpawnKitCountScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;

    private final Screen parent;
    private final ResourceLocation item;

    private String countValue;
    private EditBox countBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public SpawnKitCountScreen(Screen parent, ResourceLocation item) {
        super(Component.translatable("classloadout.gui.spawnkit_title"));
        this.parent = parent;
        this.item = item;
        Integer existing = LoadoutClientData.getSpawnKit().get(item);
        this.countValue = existing != null ? Integer.toString(existing) : "1";
    }

    @Override
    protected void init() {
        panelWidth = Math.min(200, this.width - 16);
        panelHeight = Math.min(120, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD + 12;
        countBox = new EditBox(this.font, panelLeft + PAD, y, panelWidth - 2 * PAD, 18,
                Component.translatable("classloadout.gui.spawnkit_count"));
        countBox.setMaxLength(5);
        countBox.setValue(countValue);
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        countBox.setResponder(s -> countValue = s);
        addRenderableWidget(countBox);
        y += 18 + 8;

        int bw = (panelWidth - 2 * PAD - 8) / 3;
        int bx = panelLeft + PAD;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.save"), b -> save())
                .bounds(bx, y, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.clear"), b -> clear())
                .bounds(bx, y, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.cancel"),
                        b -> minecraft.setScreen(parent))
                .bounds(bx, y, bw, 20).build());
    }

    private void save() {
        int count = countValue.isBlank() ? 1 : Integer.parseInt(countValue);
        if (count <= 0) {
            return;
        }
        command("class spawnkit add " + item + " " + count);
        minecraft.setScreen(parent);
    }

    private void clear() {
        command("class spawnkit remove " + item);
        minecraft.setScreen(parent);
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x90000000);
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + HEADER_H, COLOR_HEADER_BG);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, 0xFFFFFF);

        ItemStack stack = ItemResolver.resolve(item, LoadoutClientData.getItemVariants());
        graphics.drawString(this.font, stack != null ? stack.getHoverName() : Component.literal(item.toString()),
                l + PAD, t + HEADER_H + 2, COLOR_TEXT_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
