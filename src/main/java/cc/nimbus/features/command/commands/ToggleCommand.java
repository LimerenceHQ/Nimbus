package cc.nimbus.features.command.commands;

import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.core.Core;
import cc.nimbus.utils.client.MessageUtils;

public final class ToggleCommand extends BasicCommand {
    public ToggleCommand() { super("Toggle", "Toggles a module.", "t"); }
    @Override public void execute(String[] args) {
        if (args.length == 0) { MessageUtils.addMessage("Usage: .toggle <module>"); return; }
        BasicModule module = Core.INSTANCE.getModuleManager().getModule(args[0]);
        if (module == null) { MessageUtils.addMessage("Module not found: " + args[0]); return; }
        module.toggle();
        MessageUtils.addMessage(module.getName() + " was " + (module.isEnabled() ? "enabled" : "disabled") + '.');
    }
    @Override public String getUsage() { return ".toggle <module>"; }
    @Override public java.util.List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[0];
        return Core.INSTANCE.getModuleManager().getModules().stream().map(BasicModule::getName)
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
}
