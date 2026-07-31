package cc.nimbus.features.command.commands;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.utils.client.input.Keyboard;
import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.core.Core;
import cc.nimbus.utils.client.MessageUtils;

import java.util.*;

public final class ModulesCommand extends BasicCommand {
    public ModulesCommand() { super("Modules", "Lists registered modules.", "mods", "modulelist"); }
    @Override public void execute(String[] args) {
        List<BasicModule> modules;
        String title;
        if (args.length == 0 || args[0].equalsIgnoreCase("all")) {
            modules = Core.INSTANCE.getModuleManager().getModules();
            title = "All modules";
        } else {
            ModuleCategory category = findCategory(args[0]);
            if (category == null) { MessageUtils.addMessage("Unknown category: " + args[0]); return; }
            modules = Core.INSTANCE.getModuleManager().getModulesByCategory(category);
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
