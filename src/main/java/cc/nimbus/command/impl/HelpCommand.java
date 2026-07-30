package cc.nimbus.command.impl;

import cc.nimbus.command.BasicCommand;
import cc.nimbus.command.CommandManager;
import cc.nimbus.utils.client.MessageUtils;

public final class HelpCommand extends BasicCommand {
    public HelpCommand() { super("Help", "Displays available commands.", "?"); }
    @Override public void execute(String[] args) {
        if (args.length > 0) {
            BasicCommand command = CommandManager.getInstance().findCommand(args[0]);
            if (command == null) { MessageUtils.addMessage("\u00a7cCommand not found: \u00a7f" + args[0]); return; }
            MessageUtils.addMessage("\u00a7f" + command.getName() + " \u00a78- \u00a77" + command.getDescription());
            MessageUtils.addMessageWithoutPerfix("\u00a7a" + command.getUsage());
            if (!command.getAliases().isEmpty()) MessageUtils.addMessageWithoutPerfix("\u00a78Aliases: \u00a77" + String.join(", ", command.getAliases()));
            return;
        }
        MessageUtils.addMessage("\u00a7fNimbus commands:");
        for (BasicCommand command : CommandManager.getInstance().getCommandList()) {
            MessageUtils.addMessageWithoutPerfix("\u00a7a" + command.getUsage() + " \u00a78- \u00a77" + command.getDescription());
        }
    }
    @Override public String getUsage() { return ".help [command]"; }
    @Override public java.util.List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[0];
        return CommandManager.getInstance().getCommandList().stream().map(command -> command.getName().toLowerCase())
                .filter(name -> name.startsWith(prefix.toLowerCase())).toList();
    }
}
