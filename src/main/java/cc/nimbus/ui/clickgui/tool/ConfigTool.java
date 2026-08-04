package cc.nimbus.ui.clickgui.tool;

import cc.nimbus.core.Core;
import cc.nimbus.managers.ConfigManager;
import cc.nimbus.ui.clickgui.component.UiContext;
import cc.nimbus.ui.clickgui.component.UiRect;
import cc.nimbus.ui.clickgui.input.FocusManager;
import cc.nimbus.ui.clickgui.input.TextInput;
import cc.nimbus.ui.clickgui.theme.ClickGuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.List;

public final class ConfigTool implements ToolPage {
    private final FocusManager focus;
    private final TextInput name = new TextInput(Minecraft.getInstance().font, 32);
    private final List<Hit> hits = new ArrayList<>();
    private List<String> configs = List.of();
    private String selected;
    private String message = "";
    private long messageUntil;
    private String deleteCandidate;
    private long deleteUntil;
    private String saveCandidate;
    private long saveUntil;
    private UiRect inputBounds;
    private UiRect listBounds;
    private float scroll;

    public ConfigTool(FocusManager focus) {
        this.focus = focus;
    }

    @Override
    public void opened() {
        refresh();
    }

    @Override
    public void render(UiContext context, UiRect bounds) {
        hits.clear();
        float header = 62f;
        context.medium(context.translate("nimbus.config.title", "Config"),
                bounds.x() + 22f, bounds.y() + 18f, 19f, ClickGuiTheme.TEXT);
        context.text(context.translate("nimbus.config.description", "Manage named module configurations"),
                bounds.x() + 22f, bounds.y() + 43f,
                10.5f, ClickGuiTheme.MUTED);

        float leftWidth = Math.max(170f, bounds.width() * .39f);
        float dividerX = bounds.x() + leftWidth;
        context.frame().draw().rect(dividerX, bounds.y() + header, 1f, bounds.height() - header,
                ClickGuiTheme.STROKE);

        listBounds = new UiRect(bounds.x() + 14f, bounds.y() + header + 10f,
                leftWidth - 28f, bounds.height() - header - 58f);
        int clip = context.clip(listBounds, 3f);
        try {
            float y = listBounds.y() + scroll;
            for (String config : configs) {
                UiRect row = new UiRect(listBounds.x(), y, listBounds.width(), 29f);
                boolean active = config.equalsIgnoreCase(configs().getCurrentConfig());
                boolean chosen = config.equalsIgnoreCase(selected);
                if (chosen || row.contains(context.mouseX(), context.mouseY())) {
                    context.frame().draw().roundedRect(row.x(), row.y(), row.width(), row.height(), 3f,
                            chosen ? context.theme().accent(0x24) : ClickGuiTheme.HOVER);
                }
                if (active) context.frame().draw().roundedRect(row.x() + 7f, row.y() + 11f, 7f, 7f,
                        3.5f, 0xCCFFFFFF);
                context.textIn(config, new UiRect(row.x() + 21f, row.y(), row.width() - 28f, row.height()),
                        10.5f, chosen ? ClickGuiTheme.TEXT : ClickGuiTheme.MUTED);
                if (row.intersects(listBounds)) hits.add(new Hit(row, Action.SELECT, config, listBounds));
                y += 33f;
            }
        } finally {
            context.restore(clip);
        }

        inputBounds = new UiRect(listBounds.x(), bounds.y() + bounds.height() - 39f, listBounds.width(), 27f);
        name.render(context, inputBounds, context.translate("nimbus.config.new", "New config"), 10.5f, false);

        float rightX = dividerX + 22f;
        float rightWidth = bounds.x() + bounds.width() - rightX - 22f;
        String value = selected == null ? configs().getCurrentConfig() : selected;
        context.text(context.translate("nimbus.config.selected", "Selected"),
                rightX, bounds.y() + header + 14f, 10f, ClickGuiTheme.MUTED);
        context.medium(value, rightX, bounds.y() + header + 34f, 16f, ClickGuiTheme.TEXT);
        boolean active = value.equalsIgnoreCase(configs().getCurrentConfig());
        context.text(active
                        ? configs().isDirty()
                        ? context.translate("nimbus.config.active_dirty", "Active / Dirty")
                        : context.translate("nimbus.config.active_saved", "Active / Saved")
                        : context.translate("nimbus.config.saved", "Saved config"),
                rightX, bounds.y() + header + 59f, 10.5f,
                configs().isDirty() && active ? 0xFFE9C46A : ClickGuiTheme.MUTED);

        float buttonY = bounds.y() + header + 92f;
        button(context, new UiRect(rightX, buttonY, (rightWidth - 8f) * .5f, 30f),
                context.translate("nimbus.config.load", "Load"), Action.LOAD);
        boolean confirmingSave = value.equalsIgnoreCase(saveCandidate) && context.nowMillis() < saveUntil;
        button(context, new UiRect(rightX + (rightWidth + 8f) * .5f, buttonY,
                (rightWidth - 8f) * .5f, 30f), confirmingSave
                        ? context.translate("nimbus.config.confirm_save", "Confirm Save")
                        : context.translate("nimbus.config.save", "Save"), Action.SAVE);
        button(context, new UiRect(rightX, buttonY + 39f, (rightWidth - 8f) * .5f, 30f),
                deleteCandidate != null && context.nowMillis() < deleteUntil
                        ? context.translate("nimbus.config.confirm", "Confirm")
                        : context.translate("nimbus.config.delete", "Delete"), Action.DELETE);
        button(context, new UiRect(rightX + (rightWidth + 8f) * .5f, buttonY + 39f,
                (rightWidth - 8f) * .5f, 30f), context.translate("nimbus.config.open_folder", "Open Folder"),
                Action.OPEN);
        button(context, new UiRect(rightX, buttonY + 90f, rightWidth, 30f),
                context.translate("nimbus.config.create_current", "Create From Current"), Action.CREATE);

        if (!message.isEmpty() && context.nowMillis() < messageUntil) {
            context.text(message, rightX, bounds.y() + bounds.height() - 28f, 10.5f, ClickGuiTheme.MUTED);
        }
    }

    private void button(UiContext context, UiRect bounds, String label, Action action) {
        boolean hover = bounds.contains(context.mouseX(), context.mouseY());
        context.frame().draw().roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f,
                hover ? ClickGuiTheme.HOVER : ClickGuiTheme.CONTROL);
        context.frame().draw().outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3f, 1f,
                ClickGuiTheme.STROKE);
        context.centered(label, bounds, 10.5f, action == Action.DELETE ? ClickGuiTheme.DANGER : ClickGuiTheme.TEXT);
        hits.add(new Hit(bounds, action, null, null));
    }

    @Override
    public boolean mouseClicked(float x, float y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (inputBounds != null && inputBounds.contains(x, y)) {
            focus.focus(name);
            return true;
        }
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (!hit.bounds().contains(x, y) || hit.clip() != null && !hit.clip().contains(x, y)) continue;
            if (hit.action() == Action.SELECT) {
                selected = hit.value();
                saveCandidate = null;
                deleteCandidate = null;
                focus.clear();
            } else run(hit.action());
            return true;
        }
        focus.clear();
        return false;
    }

    @Override
    public boolean mouseScrolled(float x, float y, double delta) {
        if (listBounds == null || !listBounds.contains(x, y)) return false;
        float minimum = Math.min(0f, listBounds.height() - configs.size() * 33f);
        scroll = Math.clamp(scroll + (float) delta * 24f, minimum, 0f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!name.focused()) return false;
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            create();
            return true;
        }
        return name.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) { return name.charTyped(event); }
    @Override public boolean preeditUpdated(PreeditEvent event) { return name.preeditUpdated(event); }

    @Override
    public void closed() {
        focus.clear();
        name.value("");
        deleteCandidate = null;
        saveCandidate = null;
        message = "";
    }

    private void run(Action action) {
        switch (action) {
            case LOAD -> load();
            case SAVE -> save();
            case CREATE -> create();
            case DELETE -> delete();
            case OPEN -> openDirectory();
            case SELECT -> { }
        }
    }

    private void load() {
        if (selected != null && configs().loadConfig(selected)) show("nimbus.config.message.loaded", "Config loaded");
        else show("nimbus.config.message.select_first", "Select a config first");
    }

    private void save() {
        String target = selected == null ? configs().getCurrentConfig() : selected;
        long now = System.currentTimeMillis();
        if (!target.equalsIgnoreCase(saveCandidate) || now >= saveUntil) {
            saveCandidate = target;
            saveUntil = now + 2500L;
            show("nimbus.config.message.confirm_overwrite", "Click save again to confirm overwrite");
            return;
        }
        if (configs().saveConfig(target)) {
            selected = target;
            refresh();
            show("nimbus.config.message.saved", "Config saved");
        } else show("nimbus.config.message.save_failed", "Save failed");
        saveCandidate = null;
    }

    private void create() {
        String value = ConfigManager.sanitizeConfigName(name.value());
        if (!ConfigManager.isValidConfigName(value)) {
            show("nimbus.config.message.invalid_name", "Invalid config name");
            return;
        }
        if (configs.stream().anyMatch(config -> config.equalsIgnoreCase(value))) {
            show("nimbus.config.message.exists", "Config already exists");
            return;
        }
        if (configs().saveConfig(value)) {
            selected = value;
            name.value("");
            refresh();
            show("nimbus.config.message.created", "Config created");
        } else show("nimbus.config.message.create_failed", "Create failed");
    }

    private void delete() {
        if (selected == null) {
            show("nimbus.config.message.select_first", "Select a config first");
            return;
        }
        long now = System.currentTimeMillis();
        if (!selected.equalsIgnoreCase(deleteCandidate) || now >= deleteUntil) {
            deleteCandidate = selected;
            deleteUntil = now + 2500L;
            show("nimbus.config.message.confirm_delete", "Click delete again to confirm");
            return;
        }
        if (configs().deleteConfig(selected)) {
            selected = null;
            refresh();
            show("nimbus.config.message.deleted", "Config deleted");
        } else show("nimbus.config.message.cannot_delete", "This config cannot be deleted");
        deleteCandidate = null;
    }

    private void openDirectory() {
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop integration is unavailable");
            Desktop.getDesktop().open(configs().getConfigDir());
        } catch (Exception error) {
            show("nimbus.config.message.open_failed",
                    error.getMessage() == null ? "Unable to open directory" : error.getMessage());
        }
    }

    private void refresh() {
        configs = configs().getConfigs();
        if (selected != null && configs.stream().noneMatch(value -> value.equalsIgnoreCase(selected))) selected = null;
        scroll = 0f;
    }

    private void show(String key, String fallback) {
        message = Core.INSTANCE.getI18nManager().get(key, fallback);
        messageUntil = System.currentTimeMillis() + 2500L;
    }

    private static ConfigManager configs() { return Core.INSTANCE.getConfigManager(); }
    private enum Action { SELECT, LOAD, SAVE, CREATE, DELETE, OPEN }
    private record Hit(UiRect bounds, Action action, String value, UiRect clip) { }
}
