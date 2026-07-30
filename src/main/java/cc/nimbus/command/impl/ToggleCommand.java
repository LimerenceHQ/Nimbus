package cc.nimbus.command.impl;

import cc.nimbus.command.BasicCommand;
import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleManager;
import cc.nimbus.utils.client.MessageUtils;

public final class ToggleCommand extends BasicCommand {
    public ToggleCommand() { super("Toggle", "Toggles a module.", "t"); }
    @Override public void execute(String[] args) {
        if (args.length == 0) { MessageUtils.addMessage("Usage: .toggle <module>"); return; }
        BasicModule module = ModuleManager.getInstance().getModule(args[0]);
        if (module == null) { MessageUtils.addMessage("Module not found: " + args[0]); return; }
        module.toggle();
        MessageUtils.addMessage(module.getName() + " was " + (module.isEnabled() ? "enabled" : "disabled") + '.');
    }
    @Override public String getUsage() { return ".toggle <module>"; }
    @Override public java.util.List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[0];
        return ModuleManager.getInstance().getModules().stream().map(BasicModule::getName)
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
}
