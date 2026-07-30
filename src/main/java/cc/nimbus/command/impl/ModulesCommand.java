package cc.nimbus.command.impl;

import cc.nimbus.module.BasicModule;
import cc.nimbus.module.ModuleCategory;
import cc.nimbus.module.ModuleManager;
import cc.nimbus.utils.client.input.Keyboard;
import cc.nimbus.command.BasicCommand;
import cc.nimbus.module.*;
import cc.nimbus.utils.client.MessageUtils;

import java.util.*;

public final class ModulesCommand extends BasicCommand {
    public ModulesCommand() { super("Modules", "Lists registered modules.", "mods", "modulelist"); }
    @Override public void execute(String[] args) {
        List<BasicModule> modules;
        String title;
        if (args.length == 0 || args[0].equalsIgnoreCase("all")) {
            modules = ModuleManager.getInstance().getModules();
            title = "All modules";
        } else {
            ModuleCategory category = findCategory(args[0]);
            if (category == null) { MessageUtils.addMessage("Unknown category: " + args[0]); return; }
            modules = ModuleManager.getInstance().getModulesByCategory(category);
            title = category.name() + " modules";
        }
        MessageUtils.addMessage(title + " (" + modules.size() + "):");
        for (BasicModule module : modules) {
            String state = module.isEnabled() ? "[ON] " : "[OFF] ";
            String key = module.getKey() == 0 ? "" : " <" + Keyboard.getKeyName(module.getKey()) + ">";
            MessageUtils.addMessageWithoutPerfix(state + module.getName() + key);
        }
    }
    @Override public String getUsage() { return ".modules [category|all]"; }
    @Override public List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[0];
        List<String> values = new ArrayList<>(); values.add("all");
        Arrays.stream(ModuleCategory.values()).map(category -> category.name().toLowerCase()).forEach(values::add);
        return values.stream().filter(value -> value.startsWith(prefix.toLowerCase())).toList();
    }
    private static ModuleCategory findCategory(String value) {
        return Arrays.stream(ModuleCategory.values()).filter(category -> category.name().equalsIgnoreCase(value)).findFirst().orElse(null);
    }
}
