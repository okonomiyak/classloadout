package uk.iwaservice.classloadout.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.client.LoadoutClientData;
import uk.iwaservice.classloadout.loadout.LoadoutSlot;
import uk.iwaservice.classloadout.network.LoadoutSyncPacket;
import uk.iwaservice.classloadout.compat.ItemZoomerCompat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player-facing loadout screen, reachable only from the death screen's
 * "Loadout" button (see {@link uk.iwaservice.classloadout.ClientEvents}).
 * A bottom-anchored bar: a left column with the preset lists (tabs + text rows) and,
 * to its right, six weapon cards and four armor cards the player assigns directly by
 * clicking an {@link ItemPickerScreen}. Presets are applied as a starting point
 * (still freely editable after).
 */
public class LoadoutScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int PAD = 6;
    private static final int BAR_H = 130;
    private static final int GAP = 4;
    private static final int ICON = 16;
    private static final int CARD_H = 70;
    private static final int ARMOR = 32;
    private static final int ROW_H = 13;

    private static final int COLOR_TEXT = FlatButton.COLOR_TEXT;
    private static final int COLOR_TEXT_DIM = FlatButton.COLOR_TEXT_DIM;
    private static final int COLOR_CARD_LINE = FlatButton.COLOR_CARD_LINE;
    private static final int COLOR_ROW_HOVER = 0x30FFFFFF;
    private static final int COLOR_LOCKED = 0xFFFF5555;

    /** Which of the three preset lists is currently showing - only one at a time. */
    private enum Tab { PRESETS, MY_PRESETS, SHARED }

    /** Clickable rectangle; {@code action} runs on left click. */
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Card(LoadoutSlot slot, String labelKey, int x, int y, int w, int h) {}

    /** One text row of the left column; {@code code}/{@code delete} may be null (no "#"/"x" targets). */
    private record Row(Component label, int y, Runnable apply, @Nullable Runnable code, @Nullable Runnable delete) {}

    private final Screen returnTo;
    /** True (the regular loadout station / death screen): changes equip into the hotbar right away. False (the deferred loadout locker): only the saved data changes, taking effect on the next respawn. */
    private final boolean immediate;
    private final List<Card> cards = new ArrayList<>();
    private final List<Hit> tabHits = new ArrayList<>();
    /** Cards + list rows (the small "#"/"x" targets come first so they win over a row's apply hit). */
    private final List<Hit> hits = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Tab selectedTab = Tab.PRESETS;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int dataRevision = -1;
    private int listLeft;
    private int listW;
    private int listTop;
    private int listBottom;

    /** Immediate mode (regular loadout station / death screen). */
    public LoadoutScreen(Screen returnTo) {
        this(returnTo, true);
    }

    public LoadoutScreen(Screen returnTo, boolean immediate) {
        super(Component.translatable(immediate ? "classloadout.gui.loadout_title" : "classloadout.gui.loadout_title_deferred"));
        this.returnTo = returnTo;
        this.immediate = immediate;
    }

    @Override
    protected void init() {
        // Left of the bar is left free for Item Zoomer's zoomed item (see FlatButton#zoomMargin).
        panelLeft = FlatButton.zoomMargin(this.width, 406);
        panelWidth = this.width - panelLeft - MARGIN;
        panelHeight = BAR_H;
        panelTop = this.height - MARGIN - BAR_H;
        dataRevision = LoadoutClientData.getRevision();
        cards.clear();
        hits.clear();
        rows.clear();
        tabHits.clear();

        // Left column shrinks (90..130) so six cards of at least 46px still fit.
        int inner = panelWidth - 2 * PAD;
        int leftW = Math.max(90, Math.min(130, inner - 8 - (6 * 46 + 5 * GAP)));
        listLeft = panelLeft + PAD;
        listW = leftW;
        int cx = listLeft + leftW + 8;
        int cardsW = panelLeft + panelWidth - PAD - cx;
        int cardW = Math.max(46, Math.min(90, (cardsW - 5 * GAP) / 6));
        int cy = panelTop + 16;

        LoadoutSlot[] weapons = {LoadoutSlot.MAIN, LoadoutSlot.SIDEARM, LoadoutSlot.THROWABLE,
                LoadoutSlot.GADGET, LoadoutSlot.GADGET2, LoadoutSlot.MELEE};
        String[] weaponKeys = {"main", "sidearm", "throwable", "gadget", "gadget2", "melee"};
        for (int i = 0; i < 6; i++) {
            cards.add(new Card(weapons[i], "classloadout.gui.slot_" + weaponKeys[i], cx + i * (cardW + GAP), cy, cardW, CARD_H));
        }
        int ay = cy + CARD_H + 6;
        LoadoutSlot[] armor = {LoadoutSlot.HELMET, LoadoutSlot.CHESTPLATE, LoadoutSlot.LEGGINGS, LoadoutSlot.BOOTS};
        String[] armorKeys = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            cards.add(new Card(armor[i], "classloadout.gui.slot_" + armorKeys[i], cx + i * (ARMOR + GAP), ay, ARMOR, ARMOR));
        }
        // Picker is unrestricted (full catalog, purchase gate included) while an OP has temporarily disabled whitelist enforcement - see LoadoutManager#isWhitelistEnabled.
        for (Card c : cards) {
            hits.add(new Hit(c.x(), c.y(), c.w(), c.h(), () -> minecraft.setScreen(new ItemPickerScreen(this,
                    loc -> command("class assign " + c.slot().key() + " " + loc),
                    LoadoutClientData.isWhitelistEnabled() ? purchasable(LoadoutClientData.getWhitelist(c.slot())) : null))));
        }

        // Shop / Unselect / Close, right of the armor row.
        int btnLeft = cx + 4 * (ARMOR + GAP) + 4;
        int btnW = Math.max(36, Math.min(60, (cx + cardsW - btnLeft - 2 * GAP) / 3));
        int btnX = cx + cardsW - 3 * btnW - 2 * GAP;
        int btnY = ay + (ARMOR - 20) / 2;
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.shop_button"),
                b -> minecraft.setScreen(new ShopScreen(this)), btnX, btnY, btnW, 20));
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.class_unselect"),
                b -> command("class clear"), btnX + btnW + GAP, btnY, btnW, 20));
        addRenderableWidget(new FlatButton(Component.translatable("classloadout.gui.close"),
                b -> minecraft.setScreen(returnTo), btnX + 2 * (btnW + GAP), btnY, btnW, 20));

        // Tabs, one per line.
        int ty = panelTop + 16;
        for (Tab tab : Tab.values()) {
            int w = this.font.width(tabLabel(tab));
            tabHits.add(new Hit(listLeft, ty, w, 10, () -> {
                selectedTab = tab;
                this.init(this.minecraft, this.width, this.height);
            }));
            ty += 11;
        }

        listTop = ty + 5;
        listBottom = panelTop + panelHeight - PAD;
        int y = listTop;
        switch (selectedTab) {
            case PRESETS -> {
                for (LoadoutSyncPacket.Entry e : LoadoutClientData.getClasses()) {
                    rows.add(new Row(Component.literal(e.name()), y, () -> command("class select " + e.id()), null, null));
                    y += ROW_H;
                }
            }
            case MY_PRESETS -> {
                for (LoadoutSyncPacket.Entry e : LoadoutClientData.getPersonalPresets()) {
                    rows.add(new Row(Component.literal(e.name()), y, () -> command("class mypreset select " + e.id()),
                            () -> showCode(e.id()), () -> rawCommand("class mypreset delete " + e.id())));
                    y += ROW_H;
                }
                rows.add(new Row(Component.literal("+ ").append(Component.translatable("classloadout.gui.mypreset_save")), y,
                        () -> minecraft.setScreen(new PersonalPresetNameScreen(this)), null, null));
            }
            case SHARED -> {
                for (LoadoutSyncPacket.Entry e : LoadoutClientData.getSharedPresets()) {
                    rows.add(new Row(Component.literal(e.name()), y, () -> command("class mypreset shared " + e.id() + " select"),
                            null, () -> rawCommand("class mypreset shared " + e.id() + " clear")));
                    y += ROW_H;
                }
                rows.add(new Row(Component.translatable("classloadout.gui.mypreset_receive_button"), y,
                        () -> minecraft.setScreen(new ReceiveSharedPresetScreen(this)), null, null));
            }
        }
        for (Row r : rows) {
            if (r.delete() != null) {
                hits.add(0, new Hit(listLeft + listW - 10, r.y(), 10, ROW_H, r.delete()));
            }
            if (r.code() != null) {
                hits.add(0, new Hit(listLeft + listW - 20, r.y(), 10, ROW_H, r.code()));
            }
        }
        for (Row r : rows) {
            hits.add(new Hit(listLeft, r.y(), listW, ROW_H, r.apply()));
        }
    }

    private Component tabLabel(Tab tab) {
        return Component.translatable(switch (tab) {
            case PRESETS -> "classloadout.gui.presets_section";
            case MY_PRESETS -> "classloadout.gui.mypresets_section";
            case SHARED -> "classloadout.gui.sharedpreset_section";
        });
    }

    @Override
    public void tick() {
        if (dataRevision != LoadoutClientData.getRevision()) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (Hit h : tabHits) {
            if (h.contains(mx, my)) {
                return fire(h);
            }
        }
        boolean inList = my >= listTop && my < listBottom;
        for (Hit h : hits) {
            // Left-column hits are clipped to the visible list area.
            boolean listHit = h.x() >= listLeft && h.x() < listLeft + listW + 1;
            if (h.contains(mx, my) && (!listHit || inList)) {
                return fire(h);
            }
        }
        return false;
    }

    private boolean fire(Hit h) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        h.action().run();
        return true;
    }

    /** Drops priced items the player hasn't bought yet (see {@code /class buy}) - picking one would just silently fail to equip server-side anyway (see {@code LoadoutManager#canEquip}), so don't offer it as a choice at all. */
    private static List<ResourceLocation> purchasable(List<ResourceLocation> items) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation loc : items) {
            int price = LoadoutClientData.getPrices().getOrDefault(loc, 0);
            if (price <= 0 || LoadoutClientData.isPurchased(loc)) {
                result.add(loc);
            }
        }
        return result;
    }

    private void command(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(immediate ? cmd : cmd + " defer");
        }
    }

    /** Same as {@link #command}, but without the immediate/defer suffixing - for commands with no such variant (e.g. {@code /class mypreset delete}, which doesn't equip anything). */
    private void rawCommand(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    /**
     * Purely client-side (no server round trip needed - the id is already known locally): prints
     * the preset's own id as a click-to-copy chat message, for the player to hand out however they
     * like (chat, Discord, ...) so someone else can redeem it via {@code /class mypreset receive}.
     */
    private void showCode(UUID id) {
        if (minecraft == null) {
            return;
        }
        Component code = Component.translatable("classloadout.gui.mypreset_code", id.toString())
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id.toString()))
                        .withColor(ChatFormatting.AQUA));
        minecraft.gui.getChat().addMessage(code);
    }

    // --- rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.iconMouseX = mouseX;
        this.iconMouseY = mouseY;
        this.hoveredIcon = null;
        // No full-screen dim: the world stays visible, only a gradient behind the bar.
        graphics.fillGradient(0, panelTop - 30, this.width, this.height, 0x00000000, 0xC0000000);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.drawString(this.font, this.title, l + PAD, t + 4, COLOR_TEXT_DIM, false);
        FlatButton.drawBrackets(graphics, l, t, panelWidth, panelHeight);

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Hit h = tabHits.get(i);
            boolean active = tabs[i] == selectedTab;
            graphics.drawString(this.font, tabLabel(tabs[i]), h.x(), h.y(), active ? COLOR_TEXT : COLOR_TEXT_DIM, false);
            if (active) {
                graphics.fill(h.x(), h.y() + 10, h.x() + h.w(), h.y() + 11, COLOR_TEXT);
            }
        }

        graphics.fill(listLeft, listTop - 3, listLeft + listW, listTop - 2, COLOR_CARD_LINE);
        graphics.enableScissor(listLeft, listTop, listLeft + listW, listBottom);
        if (selectedTab == Tab.PRESETS && rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("classloadout.gui.class_none_defined"),
                    listLeft, listTop, COLOR_TEXT_DIM, false);
        }
        for (Row row : rows) {
            boolean hover = mouseX >= listLeft && mouseX < listLeft + listW
                    && mouseY >= Math.max(row.y(), listTop) && mouseY < Math.min(row.y() + ROW_H, listBottom);
            if (hover) {
                graphics.fill(listLeft, row.y(), listLeft + listW, row.y() + ROW_H, COLOR_ROW_HOVER);
            }
            int reserve = hover ? (row.code() != null ? 22 : row.delete() != null ? 12 : 0) : 0;
            String text = this.font.plainSubstrByWidth(row.label().getString(), listW - 4 - reserve);
            graphics.drawString(this.font, text, listLeft + 2, row.y() + 2, COLOR_TEXT, false);
            if (hover && row.delete() != null) {
                graphics.drawString(this.font, "x", listLeft + listW - 8, row.y() + 2, COLOR_LOCKED, false);
            }
            if (hover && row.code() != null) {
                graphics.drawString(this.font, "#", listLeft + listW - 18, row.y() + 2, 0xFF55FFFF, false);
            }
        }
        graphics.disableScissor();

        LoadoutSyncPacket.PersonalData p = LoadoutClientData.getPersonal();
        for (Card c : cards) {
            drawCard(graphics, c, locFor(p, c.slot()), mouseX, mouseY);
        }

        ItemZoomerCompat.hover(this, hoveredIcon, panelLeft, panelTop, panelWidth, panelHeight);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Nullable
    private static ResourceLocation locFor(LoadoutSyncPacket.PersonalData p, LoadoutSlot slot) {
        return switch (slot) {
            case MAIN -> p.main();
            case SIDEARM -> p.sidearm();
            case THROWABLE -> p.throwable();
            case GADGET -> p.gadget();
            case GADGET2 -> p.gadget2();
            case MELEE -> p.melee();
            case HELMET -> p.helmet();
            case CHESTPLATE -> p.chestplate();
            case LEGGINGS -> p.leggings();
            case BOOTS -> p.boots();
            default -> null;
        };
    }

    /** First icon under the mouse this frame (for Item Zoomer); reset at the top of render(). */
    @Nullable private ItemStack hoveredIcon;
    private int iconMouseX;
    private int iconMouseY;

    private void noteHover(ItemStack stack, int x, int y, int size) {
        if (hoveredIcon == null && iconMouseX >= x && iconMouseX < x + size && iconMouseY >= y && iconMouseY < y + size) {
            hoveredIcon = stack;
        }
    }

    /**
     * A saved item no longer on the slot's whitelist (an OP edit or a deleted variant, since
     * the assignment) - or a priced item the player hasn't bought via the {@link ShopScreen} (see
     * {@code LoadoutManager#canEquip}) - renders as empty here too, matching {@code
     * ServerEvents#equipLoadout}: it won't actually be equipped, so showing its icon here would be
     * misleading. Both checks are skipped for a locked slot (OP force-assigned, bypasses both by
     * design - see {@code LoadoutManager#lockSlot}), which instead gets a red dot.
     */
    private void drawCard(GuiGraphics graphics, Card c, @Nullable ResourceLocation loc, int mouseX, int mouseY) {
        LoadoutSlot slot = c.slot();
        int x = c.x();
        int y = c.y();
        int w = c.w();
        int h = c.h();
        boolean weapon = h == CARD_H;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean locked = LoadoutClientData.isLocked(slot);
        FlatButton.drawCardFrame(graphics, x, y, w, h, hover);
        if (loc != null && !locked) {
            boolean whitelisted = !LoadoutClientData.isWhitelistEnabled() || LoadoutClientData.getWhitelist(slot).contains(loc);
            int price = LoadoutClientData.getPrices().getOrDefault(loc, 0);
            boolean purchased = price <= 0 || LoadoutClientData.isPurchased(loc);
            if (!whitelisted || !purchased) {
                loc = null;
            }
        }
        ItemStack stack = loc != null ? LoadoutClientData.displayStack(loc) : null;
        int size = weapon ? 2 * ICON : ICON;
        int ix = x + (w - size) / 2;
        int iy = weapon ? y + 6 : y + (h - ICON) / 2;
        if (stack != null) {
            if (weapon) {
                graphics.pose().pushPose();
                graphics.pose().translate(ix, iy, 0);
                graphics.pose().scale(2, 2, 1);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            } else {
                graphics.renderItem(stack, ix, iy);
            }
            noteHover(stack, ix, iy, size);
        } else if (loc != null) {
            graphics.drawCenteredString(this.font, "?", x + w / 2, iy + size / 2 - 4, 0xFFFF5555);
        }
        if (locked) {
            graphics.fill(x + w - 7, y + 3, x + w - 3, y + 7, COLOR_LOCKED);
        }
        if (weapon) {
            int tw = w - 8;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable(c.labelKey()).getString(), tw),
                    x + 4, y + h - 21, COLOR_TEXT, false);
            String name = stack != null ? stack.getHoverName().getString() : "-";
            if (this.font.width(name) > tw) {
                name = this.font.plainSubstrByWidth(name, tw - this.font.width("…")) + "…";
            }
            graphics.drawString(this.font, name, x + 4, y + h - 11, stack != null ? COLOR_TEXT_DIM : 0xFF5A5F66, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
