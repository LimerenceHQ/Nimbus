package cc.nimbus.features.gateway.launch;

import cc.nimbus.features.gateway.net.GatewayProtocolException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** NetEase-specific arguments layered onto the downloaded white-client version JSON. */
public record WhiteClientLaunchParameters(
        String roleUuid,
        List<String> requiredJvmArguments,
        Map<String, String> minecraftOverrides
) {
    private static final SecureRandom RANDOM = new SecureRandom();

    public WhiteClientLaunchParameters {
        roleUuid = Objects.requireNonNull(roleUuid, "roleUuid");
        requiredJvmArguments = List.copyOf(requiredJvmArguments);
        minecraftOverrides = Map.copyOf(minecraftOverrides);
    }

    public static WhiteClientLaunchParameters create(
            GatewayLaunchTarget target,
            GatewayLaunchServices services,
            String launcherVersion,
            Path gameDirectory,
            Path assetsDirectory,
            Path nativesDirectory,
            int memoryMegabytes
    ) throws GatewayProtocolException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(services, "services");
        String version = requireValue(launcherVersion, "launcherVersion");
        if (memoryMegabytes < 512) throw new IllegalArgumentException("memoryMegabytes must be at least 512");

        Path game = absolute(gameDirectory, "gameDirectory");
        Path assets = absolute(assetsDirectory, "assetsDirectory");
        Path natives = absolute(nativesDirectory, "nativesDirectory");
        String uuid = GatewayLaunchCrypto.roleUuid(target.role().name(), target.account().userId());

        List<String> jvm = new ArrayList<>();
        jvm.add("-Xmx" + memoryMegabytes + "M");
        jvm.add("-DlauncherControlPort=" + services.authPort());
        jvm.add("-DlauncherGameId=" + target.server().gameId());
        jvm.add("-DuserId=" + target.account().userId());
        jvm.add("-DToken=" + GatewayLaunchCrypto.encryptedLauncherToken(target.account().userToken()));
        jvm.add("-DServer=RELEASE");
        jvm.add("-Djava.library.path=" + natives);
        jvm.add("-Druntime_path=" + natives.resolve("runtime"));

        Map<String, String> minecraft = new LinkedHashMap<>();
        minecraft.put("username", target.role().name());
        minecraft.put("uuid", uuid);
        minecraft.put("gameDir", game.toString());
        minecraft.put("assetsDir", assets.toString());
        minecraft.put("accessToken", "0");
        minecraft.put("server", target.server().serverHost());
        minecraft.put("port", Integer.toString(target.server().serverPort()));
        minecraft.put("userProperties", userProperties(target, services.rpcPort(), version));
        minecraft.put("userPropertiesEx", userPropertiesEx(version));
        // Match NEL/Fantnel exactly: the white client receives the target via
        // the legacy --server/--port arguments.  Quick-play options are not
        // part of their Java command and can make Forge initiate the socket
        // before the NetEase network layer has completed its launcher RPC
        // handshake, which presents as a server connection timeout.
        return new WhiteClientLaunchParameters(uuid, jvm, minecraft);
    }

    private static String userProperties(GatewayLaunchTarget target, int rpcPort, String launcherVersion)
            throws GatewayProtocolException {
        long userId;
        try {
            userId = Long.parseLong(target.account().userId());
        } catch (NumberFormatException error) {
            throw new GatewayProtocolException("网易用户 ID 无效", error);
        }
        // The official launcher passes this as a quoted JSON command-line
        // argument (the backslashes are part of the wire value).
        String filterKey = randomLowercase(32);
        return "\"{\\\"uid\\\":[" + userId + ",0],"
                + "\\\"gameid\\\":[0,0],"
                + "\\\"launcherport\\\":[" + rpcPort + ",0],"
                + "\\\"filterkey\\\":[\\\"" + filterKey + "\\\",\\\"0\\\"],"
                + "\\\"filterpath\\\":[\\\"\\\",\\\"0\\\"],"
                + "\\\"timedelta\\\":[0,0],"
                + "\\\"launchversion\\\":[\\\"" + launcherVersion + "\\\",\\\"0\\\"]}\"";
    }

    private static String userPropertiesEx(String launcherVersion) {
        JsonObject result = new JsonObject();
        result.addProperty("GameType", 2);
        result.addProperty("isFilter", true);
        result.addProperty("channel", "netease");
        result.addProperty("timedelta", 0);
        result.addProperty("launcherVersion", launcherVersion);
        // Equivalent to the official launcher's second JSON serialization:
        // serialize the JSON document once more so the command argument keeps
        // the escaped inner quotes.
        return new Gson().toJson(result.toString());
    }

    private static JsonArray pair(Number first, Number second) {
        JsonArray result = new JsonArray();
        result.add(first);
        result.add(second);
        return result;
    }

    private static JsonArray pair(String first, String second) {
        JsonArray result = new JsonArray();
        result.add(first);
        result.add(second);
        return result;
    }

    private static String randomLowercase(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return result.toString();
    }

    private static Path absolute(Path value, String name) {
        return Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    }

    private static String requireValue(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }
}
