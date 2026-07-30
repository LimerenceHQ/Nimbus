package cc.nimbus.command;

import java.util.List;

public abstract class BasicCommand {
    private final String name;
    private final String description;
    private final List<String> aliases;

    protected BasicCommand(String name, String description, String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = List.of(aliases);
    }

    public abstract void execute(String[] args);
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getAliases() { return aliases; }
    public String getUsage() { return CommandManager.PREFIX + name.toLowerCase(); }
    public List<String> suggest(String[] args) { return List.of(); }
    public boolean match(String value) {
        return name.equalsIgnoreCase(value) || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(value));
    }
}
