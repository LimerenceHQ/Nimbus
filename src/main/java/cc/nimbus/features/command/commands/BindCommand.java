package cc.nimbus.features.command.commands;

import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.core.Core;
import cc.nimbus.utils.client.MessageUtils;
import cc.nimbus.utils.client.input.Keyboard;

import java.util.List;
import java.util.OptionalInt;

public final class BindCommand extends BasicCommand {
    public BindCommand() { super("Bind", "Binds a module to a key.", "b"); }
    @Override public void execute(String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            MessageUtils.addMessage("Bound modules:");
        for (BasicModule module : Core.INSTANCE.getModuleManager().getModules()) {
                if (module.getKey() != 0) MessageUtils.addMessageWithoutPerfix("- " + module.getName() + ": " + Keyboard.getKeyName(module.getKey()));
            }
            return;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("resetall") || args[0].equalsIgnoreCase("clear"))) {
        Core.INSTANCE.getModuleManager().getModules().forEach(module -> module.setKey(0));
            MessageUtils.addMessage("All keybinds have been reset.");
            return;
        }
        if (args.length < 2) { MessageUtils.addMessage(getUsage()); return; }
        BasicModule module = Core.INSTANCE.getModuleManager().getModule(args[0]);
        if (module == null) { MessageUtils.addMessage("Module not found: " + args[0]); return; }
        OptionalInt parsed = Keyboard.findKeyIndex(args[1]);
        if (parsed.isEmpty()) {
            MessageUtils.addMessage("Unknown key: " + args[1] + ". Use NONE to unbind.");
            return;
        }
        int key = parsed.getAsInt();
        List<BasicModule> conflicts = Core.INSTANCE.getModuleManager().getModulesByKey(key).stream()
                .filter(bound -> bound != module).toList();
        module.setKey(key);
        MessageUtils.addMessage(key == 0 ? "Unbound " + module.getName() : "Bound " + module.getName() + " to " + Keyboard.getKeyName(key));
        if (!conflicts.isEmpty()) {
            MessageUtils.addMessageWithoutPerfix("Also bound: " + String.join(", ", conflicts.stream().map(BasicModule::getName).toList()));
        }
    }
    @Override public String getUsage() { return ".bind <module> <key|NONE> | list | resetall"; }
    @Override public List<String> suggest(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
        return Core.INSTANCE.getModuleManager().getModules().stream().map(BasicModule::getName)
                    .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
        }
        return List.of("NONE", "INSERT", "DELETE", "LSHIFT", "LCTRL");
    }
}
