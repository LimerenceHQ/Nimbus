package cc.nimbus.features.gateway.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record WhiteClientCommand(Path workingDirectory, List<String> command) {
    public WhiteClientCommand {
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        command = List.copyOf(command);
        if (command.isEmpty()) throw new IllegalArgumentException("command must not be empty");
    }
}
