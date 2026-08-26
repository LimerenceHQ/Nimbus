package cc.nimbus.features.module.api.setting.impl;

import cc.nimbus.features.module.api.setting.Setting;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public final class KeySetting extends Setting<KeySetting.Key> {
    public KeySetting(String name, Key value) {
        this(name, value, () -> true);
    }

    public KeySetting(String name, Key value, Supplier<Boolean> visibility) {
        super(name, Objects.requireNonNull(value, "value"), visibility);
    }

    public boolean matchesKeyboard(int keyCode) {
        Key key = getValue();
        return key.type() == InputType.KEYBOARD && key.code() == keyCode;
    }

    public boolean matchesMouse(int button) {
        Key key = getValue();
        return key.type() == InputType.MOUSE && key.code() == button;
    }

    public String displayName() {
        return getValue().displayName();
    }

    @Override
    protected Key normalize(Key value) {
        return value == null ? Key.none() : value;
    }

    public enum InputType {
        NONE,
        KEYBOARD,
        MOUSE
    }

    public record Key(InputType type, int code) {
        public Key {
            type = Objects.requireNonNull(type, "type");
            if (type == InputType.NONE) code = 0;
            else if (code < 0) throw new IllegalArgumentException("Input code cannot be negative");
        }

        public static Key none() {
            return new Key(InputType.NONE, 0);
        }

        public static Key keyboard(int keyCode) {
            return keyCode <= 0 ? none() : new Key(InputType.KEYBOARD, keyCode);
        }

        public static Key mouse(int button) {
            return button < 0 ? none() : new Key(InputType.MOUSE, button);
        }

        public String displayName() {
            return switch (type) {
                case NONE -> "NONE";
                case KEYBOARD -> cc.nimbus.utils.client.input.Keyboard.getKeyName(code);
                case MOUSE -> "MOUSE " + (code + 1);
            };
        }

        public String typeName() {
            return type.name().toUpperCase(Locale.ROOT);
        }
    }
}
