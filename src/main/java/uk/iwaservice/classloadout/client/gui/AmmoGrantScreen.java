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
import uk.iwaservice.classloadout.loadout.AmmoGrant;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * OP-only popup, opened by right-clicking a whitelisted cell in
 * {@link WhitelistEditorScreen}, that attaches an optional ammo grant to
 * that (slot, item) whitelist entry: equipping the item on respawn also
 * gives the player a fixed count of the chosen ammo item into their
 * general inventory. Mutates through the same {@code /class whitelist ammo}
 * command surface as everything else - no C2S packets.
 */
public class AmmoGrantScreen extends Screen {

    private static final int PAD = 10;
    private static final int HEADER_H = 24;
    private static final int SLOT = 24;

    private static final int COLOR_PANEL_BG = 0xF4222222;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_SLOT_BG = 0x60000000;

    private final Screen parent;
    private final LoadoutSlot slot;
    private final ResourceLocation item;

    @Nullable
    private ResourceLocation ammoItem;
    private String countValue;
    private EditBox countBox;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int ammoIconX;
    private int ammoIconY;

    public AmmoGrantScreen(Screen parent, LoadoutSlot slot, ResourceLocation item) {
        super(Component.translatable("classloadout.gui.ammo_grant_title"));
        this.parent = parent;
        this.slot = slot;
        this.item = item;
        AmmoGrant existing = LoadoutClientData.getAmmoGrant(slot, item);
        this.ammoItem = existing != null ? existing.ammoItem() : null;
        this.countValue = existing != null ? Integer.toString(existing.count()) : "";
    }

    @Override
    protected void init() {
        panelWidth = Math.min(220, this.width - 16);
        panelHeight = Math.min(164, this.height - 32);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int y = panelTop + HEADER_H + PAD;

        ammoIconX = panelLeft + PAD;
        ammoIconY = y;
        addRenderableWidget(Button.builder(Component.empty(),
                        b -> minecraft.setScreen(new ItemPickerScreen(this, loc -> {
                            ammoItem = isAir(loc) ? null : loc;
                            minecraft.setScreen(this);
                        })))
                .bounds(ammoIconX, ammoIconY, SLOT, SLOT).build());

        countBox = new EditBox(this.font, panelLeft + PAD + SLOT + 8, y + 4,
                panelWidth - 2 * PAD - SLOT - 8, 18, Component.translatable("classloadout.gui.ammo_grant_count"));
        countBox.setMaxLength(5);
        countBox.setValue(countValue);
        countBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        countBox.setResponder(s -> countValue = s);
        addRenderableWidget(countBox);
        y += SLOT + 6;

        addRenderableWidget(Button.builder(Component.translatable("classloadout.gui.whitelist_add_held"),
                        b -> registerHeldAsAmmo())
                .bounds(panelLeft + PAD, y, panelWidth - 2 * PAD, 18).build());
        y += 18 + 6;

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
        if (ammoItem == null) {
            return;
        }
        // Blank count defaults to 1 rather than blocking the save - for a single-instance item
        // (e.g. a registered ammo box variant) the count is ignored server-side anyway (it
        // always grants exactly one, see ServerEvents#grantAmmo), so typing "1" every time
        // would just be busywork.
        int count = countValue.isBlank() ? 1 : Integer.parseInt(countValue);
        if (count <= 0) {
            return;
        }
        command("class whitelist ammo " + slot.key() + " " + item + " " + ammoItem + " " + count);
        minecraft.setScreen(parent);
    }

    private void clear() {
        command("class whitelist ammo " + slot.key() + " " + item + " minecraft:air 0");
        minecraft.setScreen(parent);
    }

    /**
     * Registers the item currently in the OP's hand (full NBT included) as the ammo to
     * grant, instead of picking a bare item type from the catalog. The id is generated
     * client-side and sent along with the command so it's known immediately, rather than
     * waiting for the next {@code LoadoutSyncPacket} to see what the server picked.
     */
    private void registerHeldAsAmmo() {
        UUID id = UUID.randomUUID();
        command("class whitelist register_held " + id);
        ammoItem = new ResourceLocation("classloadout", "variant_" + id);
    }

    private static boolean isAir(ResourceLocation loc) {
        return "minecraft".equals(loc.getNamespace()) && "air".equals(loc.getPath());
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
        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);

        ItemStack itemStack = ItemResolver.resolve(item, LoadoutClientData.getItemVariants());
        graphics.drawString(this.font, itemStack != null ? itemStack.getHoverName() : Component.literal(item.toString()),
                l + PAD, t + HEADER_H + 2, COLOR_TEXT_DIM);

        graphics.fill(ammoIconX, ammoIconY, ammoIconX + SLOT, ammoIconY + SLOT, COLOR_SLOT_BG);
        if (ammoItem != null) {
            ItemStack ammoStack = ItemResolver.resolve(ammoItem, LoadoutClientData.getItemVariants());
            if (ammoStack != null) {
                graphics.renderItem(ammoStack, ammoIconX + (SLOT - 16) / 2, ammoIconY + (SLOT - 16) / 2);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
