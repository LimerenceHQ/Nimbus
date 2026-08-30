package cc.nimbus.features.gateway.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;

/** Builds the white-client command from NetEase's downloaded 1.20 version JSON. */
public final class WhiteClientCommandBuilder {
    private static final Set<String> REPLACED_JVM_PROPERTIES = Set.of(
            "launcherControlPort",
            "launcherGameId",
            "userId",
            "Token",
            "Server",
            "java.library.path",
            "runtime_path"
    );

    private WhiteClientCommandBuilder() {
    }

    public static WhiteClientCommand build(
            Path javaExecutable,
            Path gameBaseMinecraft,
            Path runtimeMinecraft,
            String minecraftVersion,
            WhiteClientLaunchParameters parameters
    ) throws IOException {
        Path java = requireFile(javaExecutable, "Java executable");
        Path base = requireDirectory(gameBaseMinecraft, "white-client base");
        Path runtime = requireDirectory(runtimeMinecraft, "white-client runtime");
        String version = requireValue(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(parameters, "parameters");

        Path versionJson = base.resolve("versions").resolve(version).resolve(version + ".json");
        JsonObject config = readObject(requireFile(versionJson, "white-client version JSON"));
        String rawJvm = stringValue(config, "jvm_arguments", "");
        String rawMinecraft = stringValue(config, "parameter_arguments", "");
        if (rawMinecraft.isBlank()) rawMinecraft = stringValue(config, "minecraftArguments", "");
        if (rawMinecraft.isBlank()) throw new IOException("White-client version JSON has no Minecraft arguments");

        String configuredMainClass = stringValue(config, "mainClass", "");
        List<String> jvmTokens = tokenize(rawJvm);
        /*
         * Forge/NetEase manifests commonly declare
         * net.minecraft.client.main.Main as mainClass, while the actual
         * jvm_arguments already contain
         * cpw.mods.bootstraplauncher.BootstrapLauncher.  The latter is the
         * Java process main class; Main is passed to BootstrapLauncher by
         * Forge's launch target machinery.  NEL follows this ordering:
         *
         *   java [all JVM options, including Nimbus -D values]
         *        cpw.mods.bootstraplauncher.BootstrapLauncher [game args]
         *
         * If we append our -D values after BootstrapLauncher, the JVM treats
         * them as application arguments and the NetEase native protocol
         * library never sees runtime_path/launcherControlPort.  Prefer the
         * bootstrap class whenever the configured main class is not present.
         */
        String mainClass = configuredMainClass;
        int bootstrapIndex = jvmTokens.indexOf("cpw.mods.bootstraplauncher.BootstrapLauncher");
        int mainClassIndex = bootstrapIndex >= 0 ? bootstrapIndex
                : (mainClass.isBlank() ? -1 : jvmTokens.indexOf(mainClass));
        if (bootstrapIndex >= 0) mainClass = "cpw.mods.bootstraplauncher.BootstrapLauncher";
        if (mainClassIndex < 0) {
            int vanillaIndex = jvmTokens.indexOf("net.minecraft.client.main.Main");
            if (vanillaIndex >= 0) {
                mainClass = "net.minecraft.client.main.Main";
                mainClassIndex = vanillaIndex;
            }
        }
        List<String> beforeMain = new ArrayList<>();
        List<String> afterMain = new ArrayList<>();
        for (int index = 0; index < jvmTokens.size(); index++) {
            String token = jvmTokens.get(index);
            if (index == mainClassIndex) continue;
            if (mainClassIndex >= 0 && index > mainClassIndex) afterMain.add(token);
            else beforeMain.add(token);
        }
        beforeMain = normalizeJvmArguments(beforeMain, base);
        beforeMain.addAll(parameters.requiredJvmArguments());
        if (mainClass.isBlank()) {
            if (beforeMain.isEmpty()) throw new IOException("White-client version JSON has no mainClass");
            mainClass = beforeMain.remove(beforeMain.size() - 1);
        }

        List<String> minecraft = replacePlaceholders(
                tokenize(rawMinecraft),
                base,
                runtime,
                version,
                parameters
        );
        for (Map.Entry<String, String> override : parameters.minecraftOverrides().entrySet()) {
            setOption(minecraft, "--" + override.getKey(), override.getValue());
        }
        writeQuickPlayDescriptor(runtime, parameters);

        List<String> command = new ArrayList<>(1 + beforeMain.size() + afterMain.size() + minecraft.size());
        command.add(java.toString());
        command.addAll(beforeMain);
        command.add(mainClass);
        command.addAll(afterMain);
        command.addAll(minecraft);
        return new WhiteClientCommand(runtime, command);
    }

    private static void writeQuickPlayDescriptor(Path runtime, WhiteClientLaunchParameters parameters) {
        try {
            String server = parameters.minecraftOverrides().getOrDefault("server", "");
            String port = parameters.minecraftOverrides().getOrDefault("port", "25565");
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("type", "multiplayer");
            root.addProperty("identifier", server + ":" + port);
            // Keep the explicit fields for launchers that use the pre-1.20.2
            // descriptor shape; unknown fields are ignored by vanilla.
            root.addProperty("host", server);
            root.addProperty("port", Integer.parseInt(port));
            Path file = runtime.resolve("quickPlay/java/nimbus-buji-island.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // The legacy server/port arguments remain in place if the
            // descriptor cannot be written.
        }
    }

    private static List<String> normalizeJvmArguments(List<String> source, Path base) throws IOException {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            String token = source.get(index).replace("${library_directory}", base.resolve("libraries").toString());
            if (token.startsWith("-Xmx")) continue;
            if (token.startsWith("-D")) {
                int equals = token.indexOf('=');
                String name = equals < 0 ? token.substring(2) : token.substring(2, equals);
                if (REPLACED_JVM_PROPERTIES.contains(name)) continue;
            }
            if (("-cp".equals(token) || "--class-path".equals(token) || "-p".equals(token)
                    || "--module-path".equals(token)) && index + 1 < source.size()) {
                result.add(token);
                result.add(resolvePathList(source.get(++index), base));
                continue;
            }
            result.add(token);
        }
        return result;
    }

    private static String resolvePathList(String value, Path base) throws IOException {
        String[] entries = value.split(";", -1);
        List<String> resolved = new ArrayList<>(entries.length);
        for (String entry : entries) {
            if (entry.isBlank()) continue;
            Path path = Path.of(entry.replace('/', java.io.File.separatorChar));
            if (!path.isAbsolute()) path = base.resolve(path);
            path = path.toAbsolutePath().normalize();
            if (!Files.exists(path)) throw new IOException("White-client classpath entry is missing: " + path);
            resolved.add(path.toString());
        }
        return String.join(";", resolved);
    }

    private static List<String> replacePlaceholders(
            List<String> source,
            Path base,
            Path runtime,
            String version,
            WhiteClientLaunchParameters parameters
    ) {
        Map<String, String> values = Map.of(
                "${game_directory}", runtime.toString(),
                "${version_name}", version,
                "${auth_player_name}", parameters.minecraftOverrides().get("username"),
                "${auth_uuid}", parameters.roleUuid(),
                "${assets_root}", base.resolve("assets").toString(),
                "${assets_index_name}", version,
                "${auth_access_token}", "0"
                ,"${quickPlayPath}", "quickPlay/java/nimbus-buji-island.json"
                ,"${quickPlayMultiplayer}", parameters.minecraftOverrides().getOrDefault("quickPlayMultiplayer", "")
        );
        // NEL/Fantnel do not use Forge's Quick Play switches for NetEase
        // servers.  The downloaded 1.20 manifest may contain them, but they
        // cause the client to initiate a vanilla connection path before the
        // NetEase launcher RPC/auth sequence is complete.  Drop the option
        // and its value (both --opt value and --opt=value forms).
        Set<String> removeOptions = new HashSet<>(Set.of(
                "--userType", "--versionType",
                "--quickPlayPath", "--quickPlayMultiplayer", "--quickPlaySingleplayer"));
        List<String> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            String token = source.get(index);
            boolean removeInline = false;
            for (String option : removeOptions) {
                if (token.startsWith(option + "=")) {
                    removeInline = true;
                    break;
                }
            }
            if (removeInline) continue;
            if (removeOptions.contains(token)) {
                if (index + 1 < source.size()) index++;
                continue;
            }
            for (Map.Entry<String, String> value : values.entrySet()) token = token.replace(value.getKey(), value.getValue());
            if (!token.contains("${")) result.add(token);
        }
        return result;
    }

    private static void setOption(List<String> arguments, String option, String value) {
        for (int index = 0; index < arguments.size(); index++) {
            String token = arguments.get(index);
            // Version manifests use both "--server host" and
            // "--server=host" spellings. Handle either exactly as the NEL
            // launcher does so a stale placeholder can never win.
            if (token.startsWith(option + "=")) {
                arguments.set(index, option + "=" + value);
                return;
            }
            if (!option.equals(token)) continue;
            if (index + 1 < arguments.size()) arguments.set(index + 1, value);
            else arguments.add(value);
            return;
        }
        arguments.add(option);
        arguments.add(value);
    }

    static List<String> tokenize(String commandLine) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int backslashes = 0;
        for (int index = 0; index < commandLine.length(); index++) {
            char character = commandLine.charAt(index);
            if (character == '\\') {
                backslashes++;
                continue;
            }
            if (character == '"') {
                current.append("\\".repeat(backslashes / 2));
                if ((backslashes & 1) == 1) current.append('"');
                else quoted = !quoted;
                backslashes = 0;
                continue;
            }
            if (backslashes > 0) {
                current.append("\\".repeat(backslashes));
                backslashes = 0;
            }
            if (Character.isWhitespace(character) && !quoted) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (backslashes > 0) current.append("\\".repeat(backslashes));
        if (quoted) throw new IOException("White-client argument string has an unterminated quote");
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private static JsonObject readObject(Path file) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException(file + " must contain a JSON object");
            return parsed.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Unable to parse white-client version JSON: " + file, error);
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Path requireFile(Path path, String label) throws IOException {
        Path value = Objects.requireNonNull(path, label).toAbsolutePath().normalize();
        if (!Files.isRegularFile(value)) throw new IOException(label + " is missing: " + value);
        return value;
    }

    private static Path requireDirectory(Path path, String label) throws IOException {
        Path value = Objects.requireNonNull(path, label).toAbsolutePath().normalize();
        if (!Files.isDirectory(value)) throw new IOException(label + " is missing: " + value);
        return value;
    }

    private static String requireValue(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }
}
