package cc.nimbus.command.impl;

import cc.nimbus.command.BasicCommand;
import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleManager;
import cc.nimbus.utils.client.MessageUtils;

public final class HideCommand extends BasicCommand {
    public HideCommand() { super("Hide", "Hides a module from ArrayList."); }
    @Override public void execute(String[] args) {
        if (args.length == 0) { MessageUtils.addMessage("Usage: .hide <module>"); return; }
        BasicModule module = ModuleManager.getInstance().getModule(args[0]);
        if (module == null) { MessageUtils.addMessage("Module not found: " + args[0]); return; }
        module.setHidden(!module.isHidden());
        MessageUtils.addMessage(module.getName() + " is now " + (module.isHidden() ? "hidden" : "visible") + '.');
    }
    @Override public String getUsage() { return ".hide <module>"; }
    @Override public java.util.List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[0];
        return ModuleManager.getInstance().getModules().stream().map(BasicModule::getName)
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
}
