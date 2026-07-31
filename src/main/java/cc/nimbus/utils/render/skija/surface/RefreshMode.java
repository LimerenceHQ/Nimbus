package cc.nimbus.utils.render.skija.surface;

import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum RefreshMode {
    Unlimited("Unlimited"),
    Limited("Limited"),
    VSync("V-Sync");

    private final String name;

    RefreshMode(String name) {
        this.name = name;
    }

    private static final RefreshMode[] VALUES = values();

    private static final Map<String, RefreshMode> BY_NAME =
            Arrays.stream(VALUES)
                    .collect(Collectors.toUnmodifiableMap(m -> m.name, m -> m));

    private static final String[] NAMES =
            Arrays.stream(VALUES)
                    .map(m -> m.name)
                    .toArray(String[]::new);

    public static RefreshMode getByName(String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    public static String[] getNames() {
        return NAMES.clone();
    }

    public int getRefreshRate(int limitedFps) {
        return switch (this) {
            case Unlimited -> Integer.MAX_VALUE;
            case Limited -> limitedFps > 0 ? limitedFps : 165;
            case VSync -> {
                int hz = Minecraft.getInstance().getWindow().getRefreshRate();
                yield hz > 0 ? hz : 165;
            }
        };
    }

    @Override
    public String toString() {
        return name;
    }
}
