package cc.nimbus.ui.clickgui.neutron.panel;

import cc.nimbus.Nimbus;
import cc.nimbus.ui.clickgui.neutron.widget.*;
import net.minecraft.client.Minecraft;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.ui.clickgui.neutron.NeutronActions;
import cc.nimbus.ui.clickgui.neutron.NeutronState;
import cc.nimbus.ui.clickgui.neutron.NeutronState.Page;
import cc.nimbus.utils.render.skija.api.SkijaFrame;
import io.github.humbleui.skija.Image;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class NeutronSidebarPanel extends NeutronPanel {
    private static final String FONT_TITLE = "SarasaUiSC-SemiBold";
    private static final String FONT_TEXT = "SarasaUiSC-Regular";
    private static final int DIVIDER = 0x1CFFFFFF;
    private static final float PAD = 18f;

    private final NeutronState state;
    private final NeutronActions actions;
    public NeutronSidebarPanel(NeutronState state, NeutronActions actions) {
        this.state = state;
        this.actions = actions;
    }

    @Override
    public void render(SkijaFrame frame, float mx, float my, boolean leftDown, boolean rightDown, boolean leftPressed, double scrolledDelta) {
        float headerH = 52f;
        float userH = 88f;
        drawLeftHeader(frame, x, y, width, headerH);
        drawLeftFooter(frame, x, y + height - userH, width, userH);
        float leftContentY = y + headerH;
        float leftContentH = height - headerH - userH;
        drawSidebar(frame, x, leftContentY, width, leftContentH, mx, my, leftPressed);
    }

    private void drawLeftHeader(SkijaFrame frame, float x, float y, float w, float h) {
        float titleX = x + PAD + 42f;
        float titleY = y + 16f;
        float iconX = x + PAD + 16f;
        float iconY = y + 26f;
        UiPaints.FILL.setColor(UiTheme.accent());
        UiDraw.snowflake(frame, iconX, iconY, 16.5f, UiPaints.FILL);

        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        frame.text().draw(FONT_TITLE, 22f, Nimbus.NAME, titleX, titleY, UiPaints.TEXT);
        UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
        String v = "Client · v" + Nimbus.VERSION;
        frame.text().draw(FONT_TEXT, 12f, v, titleX, y + 34f, UiPaints.TEXT);
        UiDraw.fillRect(frame, x + PAD, y + h - 1f, w - PAD * 2f, 1f, DIVIDER);
    }

    private void drawLeftFooter(SkijaFrame frame, float x, float y, float w, float h) {
        UiDraw.fillRect(frame, x + PAD, y, w - PAD * 2f, 1f, DIVIDER);

        float avatarSize = 44f;
        float ax = x + PAD;
        float ay = y + (h - avatarSize) / 2f;

        Minecraft mc = Minecraft.getInstance();
        Identifier skin = mc.player == null ? null : frame.images().entityTexture(mc.player);
        Image skinAvatar = skin == null ? null : frame.images().minecraftTexture(skin);
        UiDraw.drawImageRRect(frame, skinAvatar, ax, ay, avatarSize, avatarSize, 14f, 0x22000000);

        float textX = ax + avatarSize + 12f;
        String name = mc.player == null ? "NeutronUser" : mc.player.getName().getString();

        float nameY = ay + 2f;
        float nameH = 18f;
        float expireY = ay + 22f;
        float expireH = 18f;
        UiPaints.TEXT.setColor(UiColors.TEXT_PRIMARY);
        UiText.drawLeftY(frame, FONT_TITLE, 13f, name, textX, nameY, nameH, UiPaints.TEXT);
        UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
        UiText.drawLeftY(frame, FONT_TEXT, 11.5f, "本地配置", textX, expireY, expireH, UiPaints.TEXT);
    }

    private void drawSidebar(SkijaFrame frame, float x, float y, float w, float h, float mx, float my, boolean leftPressed) {
        float pad = PAD;
        float cy = y + 18f;
        float sectionGap = 13f;
        float itemH = 32f;
        float indent = 12f;

        List<SidebarSection> sections = buildSections();
        for (SidebarSection section : sections) {
            UiPaints.TEXT.setColor(UiColors.TEXT_SECONDARY);
            frame.text().draw(FONT_TITLE, 15.5f, section.title, x + pad, cy + 12f, UiPaints.TEXT);
            cy += 28f;
            for (SidebarItem item : section.items) {
                float itemX = x + pad + indent;
                float itemW = w - pad * 2f - indent;
                boolean hover = mx >= itemX && mx <= itemX + itemW && my >= cy && my <= cy + itemH;
                boolean active = item.isActive(state);
                if (hover || active) {
                    UiDraw.fillRRect(frame, itemX, cy, itemW, itemH, 12f, active ? UiColors.ACTIVE : UiColors.HOVER);
                }

                float iconSize = 18f;
                float ix = itemX + 10f;
                float iy = cy + (itemH - iconSize) / 2f;
                UiPaints.FILL.setColor(active ? UiTheme.accent() : UiColors.TEXT_SECONDARY);
                frame.draw().svg(item.iconSvg, ix, iy, iconSize, UiPaints.FILL);

                UiPaints.TEXT.setColor(active ? UiColors.TEXT_PRIMARY : UiColors.TEXT_SECONDARY);
                UiText.drawLeftY(frame, FONT_TEXT, 16f, item.title, itemX + 36f, cy, itemH, UiPaints.TEXT);

                if (hover && leftPressed) {
                    item.activate(state, actions);
                }
                cy += itemH + 6f;
            }
            cy += sectionGap;
            if (cy > y + h) break;
        }
    }

    private List<SidebarSection> buildSections() {
        List<SidebarSection> sections = new ArrayList<>();
        sections.add(new SidebarSection("Rage", List.of(
                SidebarItem.category("Combat", ModuleCategory.Combat, iconForCategory(ModuleCategory.Combat)),
                SidebarItem.category("Movement", ModuleCategory.Move, iconForCategory(ModuleCategory.Move))
        )));
        sections.add(new SidebarSection("Player", List.of(
                SidebarItem.category("Player", ModuleCategory.Player, iconForCategory(ModuleCategory.Player)),
                SidebarItem.category("Ghost", ModuleCategory.Ghost, iconForCategory(ModuleCategory.Ghost))
        )));
        sections.add(new SidebarSection("Visual", List.of(
                SidebarItem.category("Render", ModuleCategory.Render, iconForCategory(ModuleCategory.Render)),
                SidebarItem.category("HUD", ModuleCategory.HUD, iconForCategory(ModuleCategory.HUD))
        )));
        sections.add(new SidebarSection("Other", List.of(
                SidebarItem.category("Exploit", ModuleCategory.Exploit, iconForCategory(ModuleCategory.Exploit))
        )));
        sections.add(new SidebarSection("System", List.of(
                SidebarItem.page("CONFIG", Page.CONFIG, UiIcons.CONFIG)
        )));
        return sections;
    }

    private String iconForCategory(ModuleCategory category) {
        return switch (category) {
            case Combat -> UiIcons.COMBAT;
            case Move -> UiIcons.MOVEMENT;
            case Player -> UiIcons.PLAYER;
            case Render -> UiIcons.RENDER;
            case Exploit -> UiIcons.EXPLOIT;
            case Ghost -> UiIcons.GHOST;
            case HUD -> UiIcons.HUD;
        };
    }

    private static class SidebarSection {
        final String title;
        final List<SidebarItem> items;

        SidebarSection(String title, List<SidebarItem> items) {
            this.title = title;
            this.items = items;
        }
    }

    private static class SidebarItem {
        final String title;
        final ModuleCategory category;
        final Page page;
        final String iconSvg;

        private SidebarItem(String title, ModuleCategory category, Page page, String iconSvg) {
            this.title = title;
            this.category = category;
            this.page = page;
            this.iconSvg = iconSvg;
        }

        static SidebarItem category(String title, ModuleCategory category, String iconSvg) {
            return new SidebarItem(title, category, null, iconSvg);
        }

        static SidebarItem page(String title, Page page, String iconSvg) {
            return new SidebarItem(title, null, page, iconSvg);
        }

        boolean isActive(NeutronState state) {
            if (page != null) return state.page == page;
            return state.page == Page.MODULES && state.selectedCategory == category;
        }

        void activate(NeutronState state, NeutronActions actions) {
            if (page != null) {
                actions.setPage(page);
                return;
            }
            actions.setCategory(category);
        }
    }
}
