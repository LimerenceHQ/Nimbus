package cc.nimbus.features.gateway.account;

import cc.nimbus.features.gateway.model.GatewayAccount;
import cc.nimbus.features.gateway.model.GatewayDevice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Persists Gateway secrets with Windows DPAPI scoped to the current user. */
public final class GatewayAccountStore {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int FORMAT_VERSION = 1;

    private final Path directory;
    private final Path deviceFile;
    private final Path accountsFile;

    public GatewayAccountStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        deviceFile = directory.resolve("device.json");
        accountsFile = directory.resolve("accounts.json");
    }

    public void init() throws IOException {
        Files.createDirectories(directory);
    }

    public GatewayDevice loadDevice() {
        if (!Platform.isWindows() || !Files.isRegularFile(deviceFile)) return null;
        try {
            JsonObject root = readObject(deviceFile);
            return GSON.fromJson(unprotect(requireString(root, "protected")), GatewayDevice.class);
        } catch (IOException | RuntimeException error) {
            System.err.println("[Nimbus Gateway] Unable to load the protected MPay device: " + message(error));
            return null;
        }
    }

    public void saveDevice(GatewayDevice device) throws IOException {
        if (!Platform.isWindows()) return;
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("protected", protect(GSON.toJson(Objects.requireNonNull(device, "device"))));
        writeAtomic(deviceFile, root);
    }

    public Snapshot loadAccounts() {
        if (!Platform.isWindows() || !Files.isRegularFile(accountsFile)) return new Snapshot(List.of(), "");
        try {
            JsonObject root = readObject(accountsFile);
            List<GatewayAccount> accounts = new ArrayList<>();
            if (root.has("accounts") && root.get("accounts").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("accounts")) {
                    if (!element.isJsonObject()) continue;
                    try {
                        JsonObject stored = element.getAsJsonObject();
                        GatewayAccount account = GSON.fromJson(
                                unprotect(requireString(stored, "protected")),
                                GatewayAccount.class
                        );
                        if (account != null) accounts.add(account);
                    } catch (RuntimeException error) {
                        System.err.println("[Nimbus Gateway] Ignoring a damaged protected account: " + message(error));
                    }
                }
            }
            return new Snapshot(List.copyOf(accounts), stringValue(root, "last_user_id"));
        } catch (IOException | RuntimeException error) {
            System.err.println("[Nimbus Gateway] Unable to load protected accounts: " + message(error));
            return new Snapshot(List.of(), "");
        }
    }

    public void saveAccounts(List<GatewayAccount> accounts, String lastUserId) throws IOException {
        if (!Platform.isWindows()) return;
        JsonArray storedAccounts = new JsonArray();
        for (GatewayAccount account : accounts) {
            JsonObject stored = new JsonObject();
            stored.addProperty("user_id", account.userId());
            stored.addProperty("display_name", account.displayName());
            stored.addProperty("protected", protect(GSON.toJson(account)));
            storedAccounts.add(stored);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("last_user_id", Objects.requireNonNullElse(lastUserId, ""));
        root.add("accounts", storedAccounts);
        writeAtomic(accountsFile, root);
    }

    private static String protect(String value) {
        byte[] protectedBytes = Crypt32Util.cryptProtectData(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(protectedBytes);
    }

    private static String unprotect(String value) {
        byte[] protectedBytes = Base64.getDecoder().decode(value);
        return new String(Crypt32Util.cryptUnprotectData(protectedBytes), StandardCharsets.UTF_8);
    }

    private static JsonObject readObject(Path file) throws IOException {
        JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IllegalStateException(file + " must contain a JSON object");
        return parsed.getAsJsonObject();
    }

    private static void writeAtomic(Path target, JsonObject value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String requireString(JsonObject object, String key) {
        String value = stringValue(object, key);
        if (value.isBlank()) throw new IllegalStateException("Missing " + key);
        return value;
    }

    private static String stringValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record Snapshot(List<GatewayAccount> accounts, String lastUserId) {
        public Snapshot {
            accounts = List.copyOf(accounts);
            lastUserId = Objects.requireNonNullElse(lastUserId, "");
        }
    }
}
