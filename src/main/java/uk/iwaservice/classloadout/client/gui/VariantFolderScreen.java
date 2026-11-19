package uk.iwaservice.classloadout.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.client.LoadoutClientData;

/**
 * OP-only popup, opened by hovering a held-item cell in {@link WhitelistEditorScreen} (Held
 * items tab only) and pressing F, that assigns or clears that variant's organizational folder
 * (see {@code /class variant <item> folder}). Mirrors {@link PriceCountScreen}.
 */
public class VariantFolderScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;


    private final Screen parent;
    private final ResourceLocation item;

    private String folderValue;
    private EditBox folderBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    private static final int ROW_H = 11;
    private static final int MAX_ROWS = 5;
    private final List<String> candidates = new ArrayList<>();
    private int rowsTop;

    public VariantFolderScreen(Screen parent, ResourceLocation item) {
        super(Component.translatable("classloadout.gui.variant_folder_title"));
        this.parent = parent;
        this.item = item;
        this.folderValue = LoadoutClientData.getVariantFolder(item);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(220, this.width - 16);
        int reserved = Math.min(MAX_ROWS, LoadoutClientData.getVariantFolders().size()) * ROW_H;
        panelHeight = Math.min(120 + reserved, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD + 12;
        folderBox = new EditBox(this.font, panelLeft + PAD, y, panelWidth - 2 * PAD, 18,
                Component.translatable("classloadout.gui.variant_folder_name"));
        folderBox.setMaxLength(32);
        folderBox.setResponder(s -> {
            folderValue = s;
            refreshCandidates();
        });
        folderBox.setValue(folderValue);
        addRenderableWidget(folderBox);
        setInitialFocus(folderBox);
        rowsTop = y + 18 + 2;
        y += 18 + 8 + reserved;

        int bw = (panelWidth - 2 * PAD - 8) / 3;
        int bx = panelLeft + PAD;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.save"), b -> save(), bx, y, bw, 20));
        bx += bw + 4;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.clear"), b -> clear(), bx, y, bw, 20));
        bx += bw + 4;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.cancel"), b -> minecraft.setScreen(parent), bx, y, bw, 20));
    }

    private void refreshCandidates() {
        candidates.clear();
        String cur = folderValue == null ? "" : folderValue;
        String low = cur.toLowerCase();
        List<String> prefix = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String f : LoadoutClientData.getVariantFolders()) {
            String fl = f.toLowerCase();
            if (fl.equals(low)) {
                continue;
            }
            if (fl.startsWith(low)) {
                prefix.add(f);
            } else if (fl.contains(low)) {
                other.add(f);
            }
        }
        candidates.addAll(prefix);
        candidates.addAll(other);
        while (candidates.size() > MAX_ROWS) {
            candidates.remove(candidates.size() - 1);
        }
        if (folderBox != null) {
            boolean ghost = !cur.isEmpty() && !candidates.isEmpty() && candidates.get(0).toLowerCase().startsWith(low);
            folderBox.setSuggestion(ghost ? candidates.get(0).substring(cur.length()) : null);
        }
    }

    private boolean hasPrefixCandidate() {
        return !folderValue.isEmpty() && !candidates.isEmpty()
                && candidates.get(0).toLowerCase().startsWith(folderValue.toLowerCase());
    }

    private void pick(String name) {
        folderBox.setValue(name);
        folderBox.moveCursorToEnd();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 258 && folderBox.isFocused() && hasPrefixCandidate()) {
            pick(candidates.get(0));
            return true;
        }
        if ((key == 257 || key == 335) && folderBox.isFocused()) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0) {
            pick(candidates.get(i));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private int rowAt(double mx, double my) {
        if (mx < panelLeft + PAD || mx >= panelLeft + panelWidth - PAD || my < rowsTop) {
            return -1;
        }
        int i = (int) ((my - rowsTop) / ROW_H);
        return i < candidates.size() ? i : -1;
    }

    private void save() {
        if (folderValue.isBlank()) {
            clear();
            return;
        }
        command("class variant " + item + " folder " + folderValue);
        minecraft.setScreen(parent);
    }

    private void clear() {
        command("class variant " + item + " folder clear");
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

        ItemStack stack = LoadoutClientData.displayStack(item);
        graphics.drawString(this.font, stack != null ? stack.getHoverName() : Component.literal(item.toString()),
                l + PAD, t + HEADER_H + 2, FlatButton.COLOR_TEXT_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);

        int hover = rowAt(mouseX, mouseY);
        for (int i = 0; i < candidates.size(); i++) {
            int ry = rowsTop + i * ROW_H;
            if (i == hover) {
                graphics.fill(l + PAD, ry, r - PAD, ry + ROW_H, 0x20FFFFFF);
            }
            graphics.drawString(this.font, candidates.get(i), l + PAD + 2, ry + 1,
                    i == hover ? FlatButton.COLOR_TEXT : FlatButton.COLOR_TEXT_DIM);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
