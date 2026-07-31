package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.ui.alt.microsoft.MicrosoftAuth;
import cc.nimbus.ui.alt.model.AccountEnum;
import cc.nimbus.ui.alt.model.Alt;
import cc.nimbus.ui.alt.model.MicrosoftAlt;
import cc.nimbus.ui.alt.model.OfflineAlt;
import cc.nimbus.ui.alt.model.OriginalAlt;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AccountManager implements InstanceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;
    private final Path accountsFile;
    private final Path lastAccountFile;
    private final List<Alt> accounts = new ArrayList<>();
    private Alt lastAccount;

    public AccountManager(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        accountsFile = directory.resolve("accounts.json");
        lastAccountFile = directory.resolve("last.json");
    }

    @Override
    public synchronized void init() {
        accounts.clear();
        lastAccount = null;
        try {
            Files.createDirectories(directory);
            load();
        } catch (IOException | RuntimeException error) {
            accounts.clear();
            lastAccount = null;
            throw new IllegalStateException("Unable to load accounts from " + directory, error);
        }
    }

    @Override
    public synchronized void shutdown() {
        save();
        accounts.clear();
        lastAccount = null;
    }

    public synchronized List<Alt> getAccounts() {
        return List.copyOf(accounts);
    }

    public synchronized Alt getLastAccount() {
        return lastAccount;
    }

    public synchronized boolean addAccount(Alt account) {
        Objects.requireNonNull(account, "account");
        if (account.getUserName() == null || account.getUserName().isBlank()) return false;

        boolean replacesLastAccount = lastAccount != null && sameIdentity(lastAccount, account);
        accounts.removeIf(existing -> sameIdentity(existing, account));
        accounts.add(account);
        if (replacesLastAccount) lastAccount = account;
        return true;
    }

    public synchronized boolean removeAccount(Alt account) {
        if (account == null) return false;
        Alt stored = accounts.stream()
                .filter(existing -> sameIdentity(existing, account))
                .findFirst()
                .orElse(null);
        if (stored == null) return false;
        accounts.remove(stored);
        if (lastAccount != null && sameIdentity(lastAccount, stored)) lastAccount = null;
        return true;
    }

    public synchronized boolean setLastAccount(Alt account) {
        if (account == null) {
            lastAccount = null;
            return save();
        }
        Alt stored = accounts.stream()
                .filter(existing -> sameIdentity(existing, account))
                .findFirst()
                .orElse(null);
        if (stored == null) return false;
        lastAccount = stored;
        return save();
    }

    public void login(
            Alt account,
            MicrosoftAuth.Callback<MicrosoftAuth.Data> callback,
            MicrosoftAuth.Callback<String> status
    ) {
        Objects.requireNonNull(account, "account");
        if (account instanceof OfflineAlt) {
            applyUser(account.getUserName(), UUID.randomUUID(), "");
            setLastAccount(account);
        } else if (account instanceof OriginalAlt original) {
            applyUser(original.getUserName(), parseUuid(original.getUUID()), original.getAccessToken());
            setLastAccount(account);
        } else if (account instanceof MicrosoftAlt microsoft) {
            new Thread(() -> loginMicrosoft(account, microsoft, callback, status), "nimbus-alt-login").start();
        }
    }

    public synchronized boolean save() {
        try {
            Files.createDirectories(directory);
            Files.writeString(accountsFile, GSON.toJson(serializeAccounts()), StandardCharsets.UTF_8);
            if (lastAccount == null) {
                Files.deleteIfExists(lastAccountFile);
            } else {
                Files.writeString(lastAccountFile, GSON.toJson(serializeLastAccount()), StandardCharsets.UTF_8);
            }
            return true;
        } catch (IOException | RuntimeException error) {
            System.err.println("[Nimbus] Failed to save accounts: " + message(error));
            return false;
        }
    }

    private void loginMicrosoft(
            Alt account,
            MicrosoftAlt microsoft,
            MicrosoftAuth.Callback<MicrosoftAuth.Data> callback,
            MicrosoftAuth.Callback<String> status
    ) {
        try {
            new MicrosoftAuth(data -> {
                applyUser(data.userName, parseUuid(data.uuid), data.accessToken);
                setLastAccount(account);
                if (callback != null) callback.call(data);
            }, status, microsoft.getRefreshToken());
        } catch (RuntimeException error) {
            if (status != null) status.call("Login failed: " + message(error));
        }
    }

    private JsonArray serializeAccounts() {
        JsonArray result = new JsonArray();
        for (Alt account : accounts) {
            JsonObject json = new JsonObject();
            json.addProperty("AltType", account.getAccountType().getWriteName());
            json.addProperty("UserName", account.getUserName());
            if (account instanceof MicrosoftAlt microsoft) {
                json.addProperty("RefreshToken", microsoft.getRefreshToken());
            } else if (account instanceof OriginalAlt original) {
                json.addProperty("Type", original.getType());
                json.addProperty("AccessToken", original.getAccessToken());
                json.addProperty("UUID", original.getUUID());
            }
            result.add(json);
        }
        return result;
    }

    private JsonObject serializeLastAccount() {
        JsonObject result = new JsonObject();
        result.addProperty("UserName", lastAccount.getUserName());
        result.addProperty("AltType", lastAccount.getAccountType().getWriteName());
        return result;
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(accountsFile)) return;

        JsonElement root = JsonParser.parseString(Files.readString(accountsFile, StandardCharsets.UTF_8));
        if (!root.isJsonArray()) throw new IllegalStateException("Account file must contain a JSON array");
        for (JsonElement element : root.getAsJsonArray()) loadAccount(element);

        if (!Files.isRegularFile(lastAccountFile)) return;
        JsonObject json = JsonParser.parseString(
                Files.readString(lastAccountFile, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        String name = stringValue(json, "UserName");
        String type = stringValue(json, "AltType");
        lastAccount = accounts.stream()
                .filter(account -> account.getUserName().equalsIgnoreCase(name))
                .filter(account -> account.getAccountType().getWriteName().equals(type))
                .findFirst()
                .orElse(null);
    }

    private void loadAccount(JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject json = element.getAsJsonObject();
        AccountEnum type = AccountEnum.parse(stringValue(json, "AltType"));
        String name = stringValue(json, "UserName");
        if (type == null || name.isBlank()) return;

        switch (type) {
            case OFFLINE -> addAccount(new OfflineAlt(name));
            case MICROSOFT -> addAccount(new MicrosoftAlt(name, stringValue(json, "RefreshToken")));
            case ORIGINAL -> addAccount(new OriginalAlt(
                    name,
                    stringValue(json, "AccessToken"),
                    stringValue(json, "UUID")
            ));
        }
    }

    private static boolean sameIdentity(Alt first, Alt second) {
        return first.getAccountType() == second.getAccountType()
                && first.getUserName().equalsIgnoreCase(second.getUserName());
    }

    private static String stringValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return UUID.nameUUIDFromBytes("nimbus-alt".getBytes(StandardCharsets.UTF_8));
        }
        return UUID.fromString(value.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        ));
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void applyUser(String name, UUID uuid, String accessToken) {
        Minecraft.getInstance().execute(() -> {
            try {
                var field = Minecraft.class.getDeclaredField("user");
                field.setAccessible(true);
                field.set(Minecraft.getInstance(), new net.minecraft.client.User(
                        name,
                        uuid,
                        accessToken,
                        Optional.empty(),
                        Optional.empty()
                ));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to replace Minecraft user", error);
            }
        });
    }
}
