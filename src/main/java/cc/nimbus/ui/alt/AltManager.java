package cc.nimbus.ui.alt;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import cc.nimbus.config.ConfigManager;
import cc.nimbus.ui.alt.model.*;
import cc.nimbus.ui.alt.microsoft.MicrosoftAuth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AltManager {
    public static final AltManager INSTANCE = new AltManager();
    private final Path file;
    private final Path lastFile;
    private final List<Alt> accounts = new ArrayList<>();
    private Alt lastAlt;

    private AltManager() {
        Path dir;
        try {
            dir = ConfigManager.getConfigDir().toPath();
        } catch (IllegalStateException ignored) {
            dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("nimbus").resolve("configs");
        }
        file = dir.resolve("alts.json");
        lastFile = dir.resolve("lastalt.json");
        try { load(); } catch (IOException ignored) { }
    }

    public List<Alt> getAltList() { return accounts; }
    public Alt getLastAlt() { return lastAlt; }

    public void addAlt(Alt alt) {
        if (alt == null || alt.getUserName().isBlank()) return;
        accounts.removeIf(a -> a.getUserName().equalsIgnoreCase(alt.getUserName()) && a.getAccountType() == alt.getAccountType());
        accounts.add(alt);
    }

    public void removeAlt(Alt alt) { accounts.remove(alt); if (lastAlt == alt) lastAlt = null; }

    public void setLastAlt(Alt alt) { lastAlt = alt; try { save(); } catch (IOException ignored) { } }

    public void login(Alt alt, MicrosoftAuth.Callback<MicrosoftAuth.Data> callback, MicrosoftAuth.Callback<String> status) {
        if (alt instanceof OfflineAlt) {
            applyUser(alt.getUserName(), UUID.randomUUID(), "");
            setLastAlt(alt);
        } else if (alt instanceof OriginalAlt original) {
            applyUser(original.getUserName(), parseUuid(original.getUUID()), original.getAccessToken());
            setLastAlt(alt);
        } else if (alt instanceof MicrosoftAlt microsoft) {
            new Thread(() -> {
                try {
                    new MicrosoftAuth(data -> {
                        applyUser(data.userName, parseUuid(data.uuid), data.accessToken);
                        setLastAlt(alt);
                        if (callback != null) callback.call(data);
                    }, status, microsoft.getRefreshToken());
                } catch (RuntimeException e) { if (status != null) status.call("登录失败: " + message(e)); }
            }, "nimbus-alt-login").start();
        }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        JsonArray array = new JsonArray();
        for (Alt alt : accounts) {
            JsonObject json = new JsonObject();
            json.addProperty("AltType", alt.getAccountType().getWriteName());
            json.addProperty("UserName", alt.getUserName());
            if (alt instanceof MicrosoftAlt a) json.addProperty("RefreshToken", a.getRefreshToken());
            if (alt instanceof OriginalAlt a) { json.addProperty("Type", a.getType()); json.addProperty("AccessToken", a.getAccessToken()); json.addProperty("UUID", a.getUUID()); }
            array.add(json);
        }
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(array), StandardCharsets.UTF_8);
        if (lastAlt != null) {
            JsonObject last = new JsonObject(); last.addProperty("UserName", lastAlt.getUserName()); last.addProperty("AltType", lastAlt.getAccountType().getWriteName());
            Files.writeString(lastFile, last.toString(), StandardCharsets.UTF_8);
        }
    }

    private void load() throws IOException {
        if (!Files.exists(file)) return;
        accounts.clear();
        for (JsonElement element : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
            JsonObject j = element.getAsJsonObject();
            AccountEnum type = AccountEnum.parse(value(j, "AltType"));
            String name = value(j, "UserName");
            if (type == AccountEnum.OFFLINE) addAlt(new OfflineAlt(name));
            if (type == AccountEnum.MICROSOFT) addAlt(new MicrosoftAlt(name, value(j, "RefreshToken")));
            if (type == AccountEnum.ORIGINAL) addAlt(new OriginalAlt(name, value(j, "AccessToken"), value(j, "UUID")));
        }
        if (Files.exists(lastFile)) {
            JsonObject j = JsonParser.parseString(Files.readString(lastFile)).getAsJsonObject();
            String name = value(j, "UserName"), type = value(j, "AltType");
            lastAlt = accounts.stream().filter(a -> a.getUserName().equals(name) && a.getAccountType().getWriteName().equals(type)).findFirst().orElse(null);
        }
    }

    private static String value(JsonObject j, String key) { return j.has(key) && !j.get(key).isJsonNull() ? j.get(key).getAsString() : ""; }
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return UUID.nameUUIDFromBytes("nimbus-alt".getBytes(StandardCharsets.UTF_8));
        return UUID.fromString(value.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }
    private static String message(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static void applyUser(String name, UUID uuid, String accessToken) {
        Minecraft.getInstance().execute(() -> {
            try {
                var field = Minecraft.class.getDeclaredField("user");
                field.setAccessible(true);
                field.set(Minecraft.getInstance(), new net.minecraft.client.User(name, uuid, accessToken, Optional.empty(), Optional.empty()));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to replace Minecraft user", e);
            }
        });
    }
}