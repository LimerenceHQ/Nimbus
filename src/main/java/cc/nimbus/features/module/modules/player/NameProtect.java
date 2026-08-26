package cc.nimbus.features.module.modules.player;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(
        name = "NameProtect",
        description = "Replaces your name in vanilla Minecraft text.",
        category = ModuleCategory.Player,
        enableOnStartUp = true
)
public final class NameProtect extends BasicModule {
    private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);
    private static final Pattern NAME_TOKEN = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]{3,16}(?![A-Za-z0-9_])");
    private static final Pattern DISPLAY_NAME_TOKEN = Pattern.compile("([\\p{L}\\p{N}_]{1,32})$");
    private static final long NAME_REFRESH_INTERVAL_MS = 100L;
    private static final long CHAT_ECHO_TIMEOUT_MS = 5_000L;
    private static final long AUTO_PROBE_DELAY_MS = 1_000L;
    private static final String AUTO_PROBE_MESSAGE = "WHO I AM?";

    public final TextSetting name = new TextSetting("Nick Name", "Hidden");
    private List<String> cachedNames = List.of();
    private UUID cachedPlayerId;
    private long nextNameRefreshAt;
    private final Set<String> learnedNames = new LinkedHashSet<>();
    private final List<PendingChat> pendingChats = new ArrayList<>();
    private ClientPacketListener learnedConnection;
    private boolean sendingAutomaticProbe;
    private boolean automaticProbeSent;
    private long automaticProbeAt;

    public static String replace(String text) {
        if (text == null || text.isEmpty()) return text;
        NameProtect module = module();
        if (module == null || !module.isEnabled()) return text;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return text;
        String replacement = module.replacementName();
        List<String> names = module.resolveNames(minecraft);
        if (replacement.isEmpty() || names.isEmpty()) return text;
        return replacePlain(text, names, ChatFormatting.LIGHT_PURPLE + replacement + ChatFormatting.RESET);
    }

    public static Component replace(Component source) {
        if (source == null) return null;
        String replacement = currentReplacementName();
        List<String> names = protectedNames();
        if (replacement.isEmpty() || names.isEmpty() || !containsAny(source.getString(), names)) return source;

        List<StyledSegment> segments = new ArrayList<>();
        source.visit((style, text) -> {
            segments.add(new StyledSegment(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        Component result = replaceStyled(segments, names, replacement);
        return result == null ? source : result;
    }

    public static FormattedCharSequence replace(FormattedCharSequence source) {
        if (source == null) return null;
        String replacement = currentReplacementName();
        List<String> names = protectedNames();
        if (replacement.isEmpty() || names.isEmpty()) return source;

        List<StyledSegment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Style[] currentStyle = {Style.EMPTY};
        source.accept((index, style, codePoint) -> {
            if (!current.isEmpty() && !currentStyle[0].equals(style)) {
                segments.add(new StyledSegment(current.toString(), currentStyle[0]));
                current.setLength(0);
            }
            currentStyle[0] = style;
            current.appendCodePoint(codePoint);
            return true;
        });
        if (!current.isEmpty()) segments.add(new StyledSegment(current.toString(), currentStyle[0]));
        Component result = replaceStyled(segments, names, replacement);
        return result == null ? source : result.getVisualOrderText();
    }

    public static void observeOutgoingChat(String message) {
        NameProtect module = module();
        if (module == null || !module.isEnabled() || message == null || message.isBlank()) return;
        module.resetLearnedNamesIfNeeded(Minecraft.getInstance());
        long now = System.currentTimeMillis();
        module.removeExpiredPendingChats(now);
        if (module.pendingChats.size() >= 8) module.pendingChats.removeFirst();
        module.pendingChats.add(new PendingChat(
                stripLegacyFormatting(message.trim()),
                now + CHAT_ECHO_TIMEOUT_MS,
                module.sendingAutomaticProbe));
    }

    public static boolean observeIncomingChat(Component message) {
        NameProtect module = module();
        if (module == null || !module.isEnabled() || message == null) return false;

        Minecraft minecraft = Minecraft.getInstance();
        module.resetLearnedNamesIfNeeded(minecraft);
        long now = System.currentTimeMillis();
        module.removeExpiredPendingChats(now);
        if (module.pendingChats.isEmpty()) return false;

        String text = stripLegacyFormatting(message.getString()).trim();
        int pendingIndex = module.matchingPendingChat(text, true);
        if (pendingIndex < 0) pendingIndex = module.matchingPendingChat(text, false);
        if (pendingIndex < 0) return false;

        PendingChat pending = module.pendingChats.get(pendingIndex);
        String learnedName = extractSpeakerName(text, pending.body());
        if (learnedName != null && !learnedName.isBlank()) {
            module.learnedNames.add(learnedName);
            module.cachedNames = List.of();
            module.nextNameRefreshAt = 0L;
        }
        if (pending.automatic() || learnedName != null) module.pendingChats.remove(pendingIndex);
        return pending.automatic();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (!event.isPre()) return;

        Minecraft minecraft = Minecraft.getInstance();
        resetLearnedNamesIfNeeded(minecraft);
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null || minecraft.level == null
                || automaticProbeSent || !learnedNames.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (automaticProbeAt == 0L) {
            automaticProbeAt = now + AUTO_PROBE_DELAY_MS;
            return;
        }
        if (now < automaticProbeAt) return;

        automaticProbeSent = true;
        sendingAutomaticProbe = true;
        try {
            connection.sendChat(AUTO_PROBE_MESSAGE);
        } finally {
            sendingAutomaticProbe = false;
        }
    }

    public static boolean isReplacing() {
        return REPLACING.get();
    }

    public static void beginReplacing() {
        REPLACING.set(true);
    }

    public static void endReplacing() {
        REPLACING.set(false);
    }

    private static Component replaceStyled(List<StyledSegment> segments,
                                           List<String> names, String replacement) {
        StringBuilder plain = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        for (StyledSegment segment : segments) {
            plain.append(segment.text());
            for (int index = 0; index < segment.text().length(); index++) {
                styles.add(segment.style());
            }
        }
        String text = plain.toString();
        if (!containsAny(text, names)) return null;

        MutableComponent result = Component.empty();
        int from = 0;
        NameMatch nextMatch;
        while ((nextMatch = findNextMatch(text, names, from)) != null) {
            appendRange(result, text, styles, from, nextMatch.start());
            Style style = nextMatch.start() < styles.size() ? styles.get(nextMatch.start()) : Style.EMPTY;
            result.append(Component.literal(replacement)
                    .setStyle(style.withColor(ChatFormatting.LIGHT_PURPLE)));
            from = nextMatch.end();
        }
        appendRange(result, text, styles, from, text.length());
        return result;
    }

    private static String replacePlain(String text, List<String> names, String replacement) {
        StringBuilder result = new StringBuilder(text.length());
        int from = 0;
        NameMatch match;
        while ((match = findNextMatch(text, names, from)) != null) {
            result.append(text, from, match.start()).append(replacement);
            from = match.end();
        }
        return from == 0 ? text : result.append(text, from, text.length()).toString();
    }

    private static boolean containsAny(String text, List<String> names) {
        return findNextMatch(text, names, 0) != null;
    }

    private static NameMatch findNextMatch(String text, List<String> names, int from) {
        NameMatch best = null;
        for (String name : names) {
            int start = text.indexOf(name, from);
            if (start < 0) continue;
            if (best == null || start < best.start() ||
                    (start == best.start() && name.length() > best.name().length())) {
                best = new NameMatch(start, start + name.length(), name);
            }
        }
        return best;
    }

    private static void appendRange(MutableComponent target, String text, List<Style> styles,
                                    int start, int end) {
        int index = start;
        while (index < end) {
            Style style = index < styles.size() ? styles.get(index) : Style.EMPTY;
            int next = index + 1;
            while (next < end && next < styles.size() && style.equals(styles.get(next))) next++;
            target.append(Component.literal(text.substring(index, next)).setStyle(style));
            index = next;
        }
    }

    private String replacementName() {
        String value = name.getValue();
        return value == null ? "" : value.trim();
    }

    private static String currentReplacementName() {
        NameProtect module = module();
        return module == null ? "" : module.replacementName();
    }

    private static List<String> protectedNames() {
        NameProtect module = module();
        if (module == null || !module.isEnabled()) return List.of();
        return module.resolveNames(Minecraft.getInstance());
    }

    private List<String> resolveNames(Minecraft minecraft) {
        if (minecraft.player == null) return List.of();
        resetLearnedNamesIfNeeded(minecraft);

        long now = System.currentTimeMillis();
        UUID playerId = minecraft.player.getUUID();
        if (playerId.equals(cachedPlayerId) && now < nextNameRefreshAt) return cachedNames;

        Set<String> names = new LinkedHashSet<>();
        names.addAll(learnedNames);
        addName(names, minecraft.player.getName().getString());
        addName(names, minecraft.player.getGameProfile().getName());
        addName(names, minecraft.player.getScoreboardName());
        addName(names, minecraft.getUser().getName());
        addName(names, minecraft.getUser().getGameProfile().getName());
        addDisplayNames(names, minecraft.player.getDisplayName());
        addDisplayNames(names, minecraft.player.getCustomName());

        if (minecraft.getConnection() != null) {
            addName(names, minecraft.getConnection().getLocalGameProfile().getName());
            addPlayerInfo(names, minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID()));
            addPlayerInfo(names, minecraft.getConnection().getPlayerInfo(minecraft.getConnection().getId()));
            addPlayerInfo(names, minecraft.getConnection().getPlayerInfo(minecraft.getUser().getProfileId()));

            Set<String> identityNames = Set.copyOf(names);
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (identityNames.stream().anyMatch(name -> name.equalsIgnoreCase(info.getProfile().getName()))) {
                    addPlayerInfo(names, info);
                }
            }
        }

        cachedNames = names.stream()
                .filter(name -> !name.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        cachedPlayerId = playerId;
        nextNameRefreshAt = now + NAME_REFRESH_INTERVAL_MS;
        return cachedNames;
    }

    private void resetLearnedNamesIfNeeded(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == learnedConnection) return;
        learnedConnection = connection;
        learnedNames.clear();
        clearPendingChat();
        automaticProbeSent = false;
        automaticProbeAt = 0L;
        cachedNames = List.of();
        cachedPlayerId = null;
        nextNameRefreshAt = 0L;
    }

    private void clearPendingChat() {
        pendingChats.clear();
    }

    private void removeExpiredPendingChats(long now) {
        pendingChats.removeIf(pending -> now > pending.expiresAt());
    }

    private int matchingPendingChat(String text, boolean automatic) {
        for (int index = 0; index < pendingChats.size(); index++) {
            PendingChat pending = pendingChats.get(index);
            if (pending.automatic() != automatic) continue;
            if (automatic ? text.contains(pending.body()) : text.endsWith(pending.body())) return index;
        }
        return -1;
    }

    private static String extractSpeakerName(String text, String body) {
        int bodyStart = text.lastIndexOf(body);
        if (bodyStart < 0) return null;
        String prefix = text.substring(0, bodyStart).stripTrailing();
        if (!prefix.endsWith(">")) return null;
        String speaker = prefix.substring(0, prefix.length() - 1).stripTrailing();
        Matcher matcher = DISPLAY_NAME_TOKEN.matcher(speaker);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String stripLegacyFormatting(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\u00A7' && index + 1 < text.length()) {
                index++;
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }

    private static void addPlayerInfo(Set<String> names, PlayerInfo info) {
        if (info == null) return;
        addName(names, info.getProfile().getName());
        addDisplayNames(names, info.getTabListDisplayName());
    }

    private static void addDisplayNames(Set<String> names, Component displayName) {
        if (displayName == null) return;
        String text = displayName.getString().trim();
        addName(names, text);

        Matcher matcher = NAME_TOKEN.matcher(text);
        String lastToken = null;
        while (matcher.find()) lastToken = matcher.group();
        addName(names, lastToken);
    }

    private static void addName(Set<String> names, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (NAME_TOKEN.matcher(trimmed).matches()) {
            names.add(trimmed);
        }
    }

    private static NameProtect module() {
        if (!cc.nimbus.core.Core.INSTANCE.isInitialized()) return null;
        return cc.nimbus.core.Core.INSTANCE.getModuleManager().getModule(NameProtect.class);
    }

    private record StyledSegment(String text, Style style) {
    }

    private record NameMatch(int start, int end, String name) {
    }

    private record PendingChat(String body, long expiresAt, boolean automatic) {
    }
}
