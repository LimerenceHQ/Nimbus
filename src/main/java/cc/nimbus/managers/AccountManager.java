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
import java.util.function.Consumer;

public final class AccountManager implements InstanceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;
    private final Path accountsFile;
    private final Path lastAccountFile;
    private final Path legacyAccountsFile;
    private final Path legacyLastAccountFile;
    private final List<Alt> accounts = new ArrayList<>();
    private Alt lastAccount;
    private MicrosoftAuth microsoftAuth;

    public AccountManager(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        accountsFile = directory.resolve("accounts.json");
        lastAccountFile = directory.resolve("last.json");
        Path nimbusDirectory = directory.getParent();
        legacyAccountsFile = nimbusDirectory == null
                ? directory.resolve("alts.json")
                : nimbusDirectory.resolve("configs").resolve("alts.json");
        legacyLastAccountFile = nimbusDirectory == null
                ? directory.resolve("lastalt.json")
                : nimbusDirectory.resolve("configs").resolve("lastalt.json");
    }

    @Override
    public synchronized void init() {
        cancelMicrosoftLogin();
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
        cancelMicrosoftLogin();
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
            Consumer<LoginResult> callback,
            Consumer<String> status
    ) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(callback, "callback");
        Consumer<String> statusCallback = status == null ? ignored -> {} : status;

        switch (account) {
            case OfflineAlt offlineAlt -> completeLogin(
                    account,
                    account,
                    offlineUuid(account.getUserName()),
                    "",
                    callback,
                    statusCallback
            );
            case OriginalAlt original -> new Thread(
                    () -> loginOriginal(original, callback, statusCallback),
                    "nimbus-token-login"
            ).start();
            case MicrosoftAlt microsoft -> new Thread(
                    () -> loginMicrosoft(account, microsoft, callback, statusCallback),
                    "nimbus-alt-login"
            ).start();
            default -> statusCallback.accept("Login failed: unsupported account type");
        }
    }

    public void loginMicrosoft(Consumer<LoginResult> callback, Consumer<String> status) {
        Objects.requireNonNull(callback, "callback");
        Consumer<String> statusCallback = status == null ? ignored -> {} : status;
        new Thread(() -> startMicrosoftLogin(callback, statusCallback), "nimbus-microsoft-login").start();
    }

    public synchronized void cancelMicrosoftLogin() {
        if (microsoftAuth == null) return;
        microsoftAuth.close();
        microsoftAuth = null;
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
            Consumer<LoginResult> callback,
            Consumer<String> status
    ) {
        try {
            MicrosoftAuth.Data data = MicrosoftAuth.authenticate(
                    microsoft.getRefreshToken(),
                    status
            );
            completeMicrosoftLogin(account, data, callback, status);
        } catch (RuntimeException error) {
            status.accept("Login failed: " + message(error));
        }
    }

    private void loginOriginal(
            OriginalAlt original,
            Consumer<LoginResult> callback,
            Consumer<String> status
    ) {
        try {
            status.accept("Validating Minecraft access token");
            MicrosoftAuth.Profile profile = MicrosoftAuth.fetchProfile(original.getAccessToken());
            OriginalAlt authenticated = new OriginalAlt(
                    profile.userName(),
                    original.getAccessToken(),
                    profile.uuid()
            );
            completeLogin(
                    original,
                    authenticated,
                    parseUuid(profile.uuid()),
                    original.getAccessToken(),
                    callback,
                    status
            );
        } catch (RuntimeException error) {
            status.accept("Login failed: " + message(error));
        }
    }

    private void startMicrosoftLogin(Consumer<LoginResult> callback, Consumer<String> status) {
        try {
            synchronized (this) {
                cancelMicrosoftLogin();
                microsoftAuth = new MicrosoftAuth(
                        data -> completeMicrosoftLogin(null, data, callback, status),
                        status
                );
            }
        } catch (IOException | RuntimeException error) {
            status.accept("Login failed: " + message(error));
        }
    }

    private void completeMicrosoftLogin(
            Alt previous,
            MicrosoftAuth.Data data,
            Consumer<LoginResult> callback,
            Consumer<String> status
    ) {
        MicrosoftAlt authenticated = new MicrosoftAlt(data.userName, data.refreshToken);
        completeLogin(
                previous,
                authenticated,
                parseUuid(data.uuid),
                data.accessToken,
                callback,
                status
        );
    }

    private void completeLogin(
            Alt previous,
            Alt authenticated,
            UUID uuid,
            String accessToken,
            Consumer<LoginResult> callback,
            Consumer<String> status
    ) {
        applyUser(
                authenticated.getUserName(),
                uuid,
                accessToken,
                () -> {
                    boolean saved;
                    synchronized (this) {
                        if (previous != null) {
                            accounts.removeIf(existing -> sameIdentity(existing, previous));
                        }
                        accounts.removeIf(existing -> sameIdentity(existing, authenticated));
                        accounts.add(authenticated);
                        lastAccount = authenticated;
                        saved = save();
                    }
                    callback.accept(new LoginResult(authenticated, saved));
                },
                error -> status.accept("Login failed: " + message(error))
        );
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
        Path sourceAccounts = Files.isRegularFile(accountsFile) ? accountsFile : legacyAccountsFile;
        Path sourceLast = Files.isRegularFile(lastAccountFile) ? lastAccountFile : legacyLastAccountFile;
        if (!Files.isRegularFile(sourceAccounts)) return;

        JsonElement root = JsonParser.parseString(Files.readString(sourceAccounts, StandardCharsets.UTF_8));
        if (!root.isJsonArray()) throw new IllegalStateException("Account file must contain a JSON array");
        for (JsonElement element : root.getAsJsonArray()) loadAccount(element);

        if (!Files.isRegularFile(sourceLast)) {
            if (sourceAccounts.equals(legacyAccountsFile)) save();
            return;
        }
        JsonObject json = JsonParser.parseString(
                Files.readString(sourceLast, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        String name = stringValue(json, "UserName");
        String type = stringValue(json, "AltType");
        lastAccount = accounts.stream()
                .filter(account -> account.getUserName().equalsIgnoreCase(name))
                .filter(account -> account.getAccountType().getWriteName().equals(type))
                .findFirst()
                .orElse(null);
        if (sourceAccounts.equals(legacyAccountsFile)) save();
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
            throw new IllegalArgumentException("Authenticated account did not provide a UUID");
        }
        try {
            return UUID.fromString(value.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                    "$1-$2-$3-$4-$5"
            ));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Authenticated account returned an invalid UUID", error);
        }
    }

    private static UUID offlineUuid(String userName) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + userName).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void applyUser(
            String name,
            UUID uuid,
            String accessToken,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        Minecraft.getInstance().execute(() -> {
            try {
                var field = Minecraft.class.getDeclaredField("user");
                field.setAccessible(true);
                field.set(Minecraft.getInstance(), new net.minecraft.client.User(
                        name,
                        uuid,
                        accessToken,
                        Optional.empty(),
                        Optional.empty(),
                        net.minecraft.client.User.Type.MSA
                ));
            } catch (ReflectiveOperationException | RuntimeException error) {
                failure.accept(new IllegalStateException("Unable to replace Minecraft user", error));
                return;
            }
            success.run();
        });
    }

    public record LoginResult(Alt account, boolean saved) {}
}
