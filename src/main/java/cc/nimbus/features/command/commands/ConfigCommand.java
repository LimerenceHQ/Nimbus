package cc.nimbus.features.command.commands;

import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.core.Core;
import cc.nimbus.managers.ConfigManager;
import cc.nimbus.utils.client.MessageUtils;

import java.util.List;

public final class ConfigCommand extends BasicCommand {
    public ConfigCommand() { super("Config", "Manages local configurations.", "c", "configs", "cfg"); }
    @Override public void execute(String[] args) {
        if (args.length == 0) { usage(); return; }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                MessageUtils.addMessage("Local configs:");
                ConfigManager configs = Core.INSTANCE.getConfigManager();
                String current = configs.getCurrentConfig();
                configs.getConfigs().forEach(name -> MessageUtils.addMessageWithoutPerfix((name.equals(current) ? "* " : "- ") + name));
            }
            case "current" -> MessageUtils.addMessage("Current config: " + Core.INSTANCE.getConfigManager().getCurrentConfig());
            case "save" -> {
                if (!require(args, 2)) return;
                String name = args[1];
                MessageUtils.addMessage("Saving " + name + "...");
                boolean success = Core.INSTANCE.getConfigManager().saveConfig(name);
                MessageUtils.addMessage(success ? "Saved " + name : "Failed to save " + name);
            }
            case "load" -> { if (!require(args, 2)) return; MessageUtils.addMessage(Core.INSTANCE.getConfigManager().loadConfig(args[1]) ? "Loaded " + args[1] : "Failed to load " + args[1]); }
            case "delete", "remove" -> { if (!require(args, 2)) return; MessageUtils.addMessage(Core.INSTANCE.getConfigManager().deleteConfig(args[1]) ? "Deleted " + args[1] : "Failed to delete " + args[1]); }
            default -> usage();
        }
    }
    private static boolean require(String[] args, int count) { if (args.length >= count) return true; usage(); return false; }
    private static void usage() { MessageUtils.addMessage("Usage: .config list|current|save|load|delete"); }
    @Override public String getUsage() { return ".config <list|current|save|load|delete>"; }
    @Override public List<String> suggest(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return List.of("list", "current", "save", "load", "delete")
                    .stream().filter(value -> value.startsWith(prefix)).toList();
        }
        String prefix = args[args.length - 1];
        return Core.INSTANCE.getConfigManager().getConfigs().stream().filter(value -> value.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
}
