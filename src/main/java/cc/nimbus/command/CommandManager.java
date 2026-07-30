package cc.nimbus.command;

import cc.nimbus.command.impl.*;
import cc.nimbus.command.impl.*;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.handler.EventManager;
import cc.nimbus.event.impl.game.EventChatSend;
import cc.nimbus.utils.client.MessageUtils;

import java.util.*;

public final class CommandManager {
    private static final CommandManager INSTANCE = new CommandManager();
    public static final String PREFIX = ".";
    private final List<BasicCommand> commandList = new ArrayList<>();
    private final Map<String, BasicCommand> commandMap = new HashMap<>();

    private CommandManager() {}
    public static CommandManager getInstance() { return INSTANCE; }

    public synchronized void init() {
        EventManager.unregister(this);
        commandList.clear();
        commandMap.clear();
        register(new BindCommand());
        register(new ConfigCommand());
        register(new HelpCommand());
        register(new HideCommand());
        register(new ModulesCommand());
        register(new ProfileCommand());
        register(new ToggleCommand());
        EventManager.register(this);
    }

    public synchronized void shutdown() {
        EventManager.unregister(this);
        commandList.clear();
        commandMap.clear();
    }

    public synchronized void register(BasicCommand command) {
        Objects.requireNonNull(command, "command");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(normalize(command.getName()));
        for (String alias : command.getAliases()) names.add(normalize(alias));
        if (names.contains("")) throw new IllegalArgumentException("Empty command name or alias");
        for (String name : names) {
            if (commandMap.containsKey(name)) throw new IllegalArgumentException("Duplicate command name or alias: " + name);
        }
        names.forEach(name -> commandMap.put(name, command));
        commandList.add(command);
        commandList.sort(Comparator.comparing(BasicCommand::getName, String.CASE_INSENSITIVE_ORDER));
    }

    private static String normalize(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }

    public boolean executeCommand(String input) {
        if (input == null) return false;
        String commandLine = input.trim();
        if (commandLine.startsWith(PREFIX)) commandLine = commandLine.substring(PREFIX.length());
        if (commandLine.isBlank()) {
            MessageUtils.addMessage("Type .help to list commands.");
            return false;
        }
        List<String> parsed;
        try {
            parsed = CommandLine.parse(commandLine);
        } catch (IllegalArgumentException error) {
            MessageUtils.addMessage("Invalid command line: " + error.getMessage());
            return false;
        }
        if (parsed.isEmpty()) return false;
        String commandName = parsed.getFirst();
        BasicCommand command = findCommand(commandName);
        if (command == null) {
            String closest = findClosest(commandName);
            MessageUtils.addMessage("Unknown command: " + commandName
                    + (closest == null ? ". Try .help" : ". Did you mean ." + closest + "?"));
            return false;
        }
        try {
            command.execute(parsed.subList(1, parsed.size()).toArray(String[]::new));
            return true;
        } catch (RuntimeException error) {
            MessageUtils.addMessage("Failed to execute command: " + error.getMessage());
            error.printStackTrace();
            return false;
        }
    }

    public BasicCommand findCommand(String name) {
        if (name == null) return null;
        return commandMap.get(normalize(name));
    }

    public List<String> getSuggestions(String input) {
        String value = input == null ? "" : input;
        if (value.startsWith(PREFIX)) value = value.substring(PREFIX.length());
        boolean argumentPosition = value.chars().anyMatch(Character::isWhitespace);
        List<String> parsed;
        try { parsed = CommandLine.parse(value); }
        catch (IllegalArgumentException ignored) { return List.of(); }
        if (!argumentPosition) {
            String prefix = parsed.isEmpty() ? "" : parsed.getFirst().toLowerCase(Locale.ROOT);
            return commandList.stream().map(command -> command.getName().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix)).map(name -> PREFIX + name).toList();
        }
        if (parsed.isEmpty()) return List.of();
        BasicCommand command = findCommand(parsed.getFirst());
        if (command == null) return List.of();
        List<String> argumentList = new ArrayList<>(parsed.subList(1, parsed.size()));
        if (!value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1))) argumentList.add("");
        String[] args = argumentList.toArray(String[]::new);
        return command.suggest(args);
    }

    private String findClosest(String value) {
        return commandList.stream().map(command -> command.getName().toLowerCase(Locale.ROOT))
                .min(Comparator.comparingInt(name -> CommandLine.distance(value, name)))
                .filter(name -> CommandLine.distance(value, name) <= Math.max(2, value.length() / 3)).orElse(null);
    }

    @EventTarget
    public void onChatSend(EventChatSend event) {
        String message = event.getMessage();
        if (message != null && message.startsWith(PREFIX)) {
            event.cancelEvent();
            executeCommand(message.substring(PREFIX.length()));
        }
    }

    public List<BasicCommand> getCommandList() { return List.copyOf(commandList); }
}
