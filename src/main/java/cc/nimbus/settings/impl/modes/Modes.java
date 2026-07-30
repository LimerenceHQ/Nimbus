package cc.nimbus.settings.impl.modes;

import java.util.Arrays;

public final class Modes {
    private final String[] modes;
    public Modes(String... modes) {
        if (modes == null || modes.length == 0) throw new IllegalArgumentException("modes cannot be empty");
        this.modes = Arrays.copyOf(modes, modes.length);
    }
    public String[] getModes() { return Arrays.copyOf(modes, modes.length); }
}
