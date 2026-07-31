package cc.nimbus.features.command.commands;

import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.event.EventBus;
import cc.nimbus.utils.client.MessageUtils;

import java.util.Locale;

public final class ProfileCommand extends BasicCommand {
    public ProfileCommand() { super("Profile", "Profiles event hot paths.", "prof", "ep"); }
    @Override public void execute(String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start", "on" -> { EventBus.startProfiling(); MessageUtils.addMessage("Event profiler started."); }
            case "stop", "off" -> { EventBus.stopProfiling(); MessageUtils.addMessage("Event profiler stopped."); }
            case "reset", "clear" -> { EventBus.resetProfiling(); MessageUtils.addMessage("Event profiler reset."); }
            case "dump", "top", "report" -> dump(args.length > 1 ? parseLimit(args[1]) : 8);
            case "status" -> status();
            default -> MessageUtils.addMessage("Usage: .profile start|stop|status|reset|dump [limit]");
        }
    }
    private void status() {
        EventBus.ProfileSnapshot snapshot = EventBus.getProfileSnapshot(1);
        MessageUtils.addMessage("Profiler " + (snapshot.running() ? "running" : "stopped") + ", events=" + snapshot.events().size() + ", listeners=" + snapshot.listeners().size());
    }
    private void dump(int limit) {
        EventBus.ProfileSnapshot snapshot = EventBus.getProfileSnapshot(limit);
        snapshot.events().forEach(entry -> MessageUtils.addMessageWithoutPerfix(format(entry)));
        snapshot.listeners().forEach(entry -> MessageUtils.addMessageWithoutPerfix(format(entry)));
    }
    private static String format(EventBus.ProfileEntry entry) {
        return entry.name() + " total=" + String.format(Locale.ROOT, "%.3fms", entry.totalNanos() / 1_000_000.0) + " calls=" + entry.calls();
    }
    private static int parseLimit(String value) { try { return Math.max(1, Math.min(15, Integer.parseInt(value))); } catch (NumberFormatException ignored) { return 8; } }
    @Override public String getUsage() { return ".profile start|stop|status|reset|dump [limit]"; }
    @Override public java.util.List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return java.util.List.of("start", "stop", "status", "reset", "dump").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }
}
