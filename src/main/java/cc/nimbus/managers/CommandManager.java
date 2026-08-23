package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventChatSend;
import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.features.command.api.CommandLine;
import cc.nimbus.features.command.commands.BindCommand;
import cc.nimbus.features.command.commands.ConfigCommand;
import cc.nimbus.features.command.commands.HelpCommand;
import cc.nimbus.features.command.commands.HideCommand;
import cc.nimbus.features.command.commands.ModulesCommand;
import cc.nimbus.features.command.commands.MusicCommand;
import cc.nimbus.features.command.commands.NotificationTestCommand;
import cc.nimbus.features.command.commands.ProfileCommand;
import cc.nimbus.features.command.commands.ToggleCommand;
import cc.nimbus.utils.client.MessageUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class CommandManager implements InstanceManager {
    public static final String PREFIX = ".";

    private final List<BasicCommand> commands = new ArrayList<>();
    private final Map<String, BasicCommand> commandsByName = new HashMap<>();

    @Override
    public synchronized void init() {
        shutdown();
        try {
            register(new BindCommand());
            register(new ConfigCommand());
            register(new HelpCommand());
            register(new HideCommand());
            register(new ModulesCommand());
            register(new MusicCommand());
            register(new NotificationTestCommand());
            register(new ProfileCommand());
            register(new ToggleCommand());
            EventBus.register(this);
        } catch (RuntimeException error) {
            shutdown();
            throw error;
        }
    }

    @Override
    public synchronized void shutdown() {
        EventBus.unregister(this);
        commands.clear();
        commandsByName.clear();
    }

    public synchronized void register(BasicCommand command) {
        Objects.requireNonNull(command, "command");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(normalize(command.getName()));
        for (String alias : command.getAliases()) names.add(normalize(alias));
        if (names.contains("")) throw new IllegalArgumentException("Empty command name or alias");

        for (String name : names) {
            if (commandsByName.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate command name or alias: " + name);
            }
        }

        names.forEach(name -> commandsByName.put(name, command));
        commands.add(command);
        commands.sort(Comparator.comparing(BasicCommand::getName, String.CASE_INSENSITIVE_ORDER));
    }

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
            String closest = findClosest(commandName, getCommandList());
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

    public synchronized BasicCommand findCommand(String name) {
        return name == null ? null : commandsByName.get(normalize(name));
    }

    public List<String> getSuggestions(String input) {
        String value = input == null ? "" : input;
        if (value.startsWith(PREFIX)) value = value.substring(PREFIX.length());
        boolean argumentPosition = value.chars().anyMatch(Character::isWhitespace);

        List<String> parsed;
        try {
            parsed = CommandLine.parse(value);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }

        if (!argumentPosition) {
            String prefix = parsed.isEmpty() ? "" : parsed.getFirst().toLowerCase(Locale.ROOT);
            return getCommandList().stream()
                    .map(command -> command.getName().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .map(name -> PREFIX + name)
                    .toList();
        }
        if (parsed.isEmpty()) return List.of();

        BasicCommand command = findCommand(parsed.getFirst());
        if (command == null) return List.of();
        List<String> arguments = new ArrayList<>(parsed.subList(1, parsed.size()));
        if (!value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1))) arguments.add("");
        return command.suggest(arguments.toArray(String[]::new));
    }

    public synchronized List<BasicCommand> getCommandList() {
        return List.copyOf(commands);
    }

    @EventTarget
    public void onChatSend(EventChatSend event) {
        String message = event.getMessage();
        if (message != null && message.startsWith(PREFIX)) {
            event.cancelEvent();
            executeCommand(message.substring(PREFIX.length()));
        }
    }

    private static String findClosest(String value, List<BasicCommand> commands) {
        return commands.stream()
                .map(command -> command.getName().toLowerCase(Locale.ROOT))
                .min(Comparator.comparingInt(name -> CommandLine.distance(value, name)))
                .filter(name -> CommandLine.distance(value, name) <= Math.max(2, value.length() / 3))
                .orElse(null);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
