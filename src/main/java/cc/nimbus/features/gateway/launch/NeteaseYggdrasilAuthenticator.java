package cc.nimbus.features.gateway.launch;

import cc.nimbus.features.gateway.model.WhiteClientArtifact;
import cc.nimbus.features.gateway.model.WhiteClientInstallation;
import cc.nimbus.features.gateway.net.GatewayProtocolException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Official NetEase Yggdrasil handshake used by the white client AuthSock callback. */
public final class NeteaseYggdrasilAuthenticator implements GatewayJoinAuthenticator {
    private static final URI AUTH_SERVER_LIST = URI.create("https://x19.update.netease.com/authserver.list");
    private static final URI PATCH_LIST = URI.create("https://x19.update.netease.com/pl/x19_java_patchlist");
    private static final URI CRC_SALT_ENDPOINT = URI.create("https://service.codexus.today/crc-salt");
    private static final String CRC_SALT_ACCESS_TOKEN =
            "0e9327a2-d0f8-41d5-8e23-233de1824b9a.pk_053ff2d53503434bb42fe158";
    private static final byte[] NONCE = "163 NetEase\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TOKEN_KEY = HexFormat.of().parseHex("AC249C69C72CB3B44EC0CC6C543A8195");
    private static final byte[] TCP_SALT = HexFormat.of().parseHex("2F84AEA399212926DABF95A3ABAF37E0");
    private static final byte[] MC_VERSION_SALT = HexFormat.of().parseHex("01000480D23EF71101");
    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4HJFrYdVTeoSvH6qsnJElfXuf7FnxxFQdz3gRCs66LDrZfaoGoWt2e/aGIOv8uGHliBWnZP42Ike9Qf5aiYVtQ4mlj2bXZjifHG35LlS1Bq6yCA6k1WevWcrGWOuLzny3jo8Wbdi0lIFMTT2hN98sF2k4YcvyE9zhqxfRNFGVI5kLyxm9CeTKAXGBU5mw3yQWJ8cPRR4866jpGGOhBWlJdilWt2NES9bid8SbhTT55wqumnVO5J5/DaMyTgKIQngH7NyZQljAhdK5I23dzpGop322n2eQ+mTNLuquwU453o1cbyQobgC6vh5/F1QT2INBR2qYCnRzzJ6hrhE5kIMZwIDAQAB";
    private static final String PRIVATE_KEY = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDobJGIddxcs08xzTIVFanc4/84J1DbxiW7wLokIrap3txzyyMXj+AcDa8jLopLJkMg9rZLzL50Dwp+hmTTgiIcjkM1DVREsRBltzqjVNyiYPL2VGHLn+/eEivjhLNWUMcWrlAoJ5JJBi22oGPLlKDVJpg33JPI5nPZpwufdB0ecn2V0CAeeGyswQyAaqIjoiYOoP3HipjEYGQHp1RsADf4ozGRgv+2HGiSOyhlv00ixnF0nRUTzVh18ka0N3LdLMpAN/YAPkO8tmoWp0asyU3X+nyZFd29povvrRy4rL1lYmo8jdpfpSL+Yk+9+RybjBAhXRx6uDoBxaa2kwE4fyrrAgMBAAECggEAWT42szruHfoLkofDjyz+R/6TZLBT788pdeoOjwl1McyMwTlihA2Oc7cdZFjeaPSMGgAhBwHarx2HXgWkeUIibuyBCcHQdX+3WBb+wPA4t3CaWdMUqecDZzV6/KVbZu0lRKQxyvlGxhtFOjZjmyu6hZ2IHQrpA97Y5N2rLNKcy69W+QYJZappmBfbVgWM0NRmgmpg6siQ0Cm6Ryil3SBAPBVv+EUPiD9jdXbtVq7VwN4YmwUGScp2Fib1oUnqEAja1hfihVnRFQ246nKXhIc/YVrNmwBrxAVwaaPFRka6XjKkSF0WVbpqQLXhbY1fS8FoXGpVhiF6o4rTQJbpQxhQgQKBgQD7XysfPNt9G63gTrkZvjvEk5LKsRG42MAYEkuzxEal9E/AQv0jJrk0f1WO09hCcYQeaOQXhM9mezNQv5jXEnmepXqM3NTw1Di5yh3uvjSXQjdUt+7haNw+QjggBqZxyQZjtadYairSzfmWe7OwJIkCmdgaJKxm4qMExk9kUgApGwKBgQDstBdJHU/KEBqVpsIlu185vFFuaAvxiHjXHqGwytJMQ/5aVqaphIiQCAxaEogPSzPSm28UHVQiZeFO059EKOpSJscW4pV95Dr5BAbHuYecacqnZKbQqb69//Cfpne9tGYXlmP6QnYfPoc4wYfTfPyU2x3KtDhVxtEDutpSNU1ycQKBgBabWn92s66uvJZ9vfvotetZ8ku0XQmoxK3lh1Vlg40NSdbar3Vn2CQ2h3VO7BYdq2oouMq8sQJgdh7+/DnreXChJUJh4ey+yVM8MDD2fjhURjGiUSOIkLYwsmd+8Z0uHRr+jUxQUAWhbJ7yBRkEUCYhu+OuBKtEGrElPKKjFUydAoGAQLj9pQBe0OGWY1U1wRt67k6P9aB9o42tfSTjEXRkDHaLFiibab7TmI6a0gY/Le9iPDREKzvZxY4WDXfQFNMbP1tbFObf+Yxuk6iGMhaI/jvvLdZXxrajcVCKex0JoNWzFMAKlmOV6PUwBFTmzu1eI1XGz6Z3wPycKmjtSY1JoAECgYBBOfaUDMaG1xLzv+q1jPPs2U4lXPK2BXFE5RaliUGC+LIQREXPishII2LYFW3gtXj5QWfIGq6x0d6ca6Bja2vYRDDe5tlT/2VbZahiHpb2PYL/2WgeoHl7sT9Bb/nsKyo85Sv+doop6huy4+aeTiQHgrGR9JYMVBSIx6P8Tt5phA==";

    private final GatewayLaunchTarget target;
    private final String crcSalt;
    private final String bootstrapMd5;
    private final String datMd5;
    private final String modInfo;
    private final String launcherVersion;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile List<Endpoint> endpoints;

    public NeteaseYggdrasilAuthenticator(GatewayLaunchTarget target, WhiteClientInstallation installation)
            throws GatewayProtocolException {
        this.target = Objects.requireNonNull(target, "target");
        this.crcSalt = loadCrcSalt();
        String[] md5 = md5For(target.server().gameVersion());
        this.bootstrapMd5 = md5[0];
        this.datMd5 = md5[1];
        this.modInfo = modInfo(installation);
        this.launcherVersion = loadLauncherVersion();
        System.out.println("[Nimbus Gateway] Yggdrasil parameters: launcherVersion="
                + launcherVersion + ", crcSalt=" + redact(crcSalt)
                + ", gameVersion=" + target.server().gameVersion()
                + ", bootstrapMd5=" + bootstrapMd5 + ", datMd5=" + datMd5);
    }

    @Override
    public boolean authenticate(String serverId) throws Exception {
        if (serverId == null || serverId.isBlank()) return false;
        Endpoint endpoint = endpoint();
        System.out.println("[Nimbus Gateway] Yggdrasil endpoint: " + endpoint.host() + ":" + endpoint.port());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 10_000);
            socket.setSoTimeout(15_000);
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            byte[] initial = frameRead(input);
            if (initial.length < 272) throw new IOException("Yggdrasil 初始化响应长度无效");
            byte[] seed = java.util.Arrays.copyOfRange(initial, 0, 16);
            byte[] signContent = java.util.Arrays.copyOfRange(initial, 16, 272);
            frameWrite(output, initialize(seed, signContent));
            byte[] response = frameRead(input);
            if (response.length < 1) {
                System.err.println("[Nimbus Gateway] Yggdrasil initialization returned an empty response");
                return false;
            }
            if (response[0] != 0) {
                System.err.println("[Nimbus Gateway] Yggdrasil initialization rejected, status=" + (response[0] & 0xFF));
                return false;
            }
            byte[] request = join(serverId, seed);
            byte[] encrypted = packMessage(9, request, concat(tokenBytes(), seed));
            frameWrite(output, encrypted);
            byte[] result = unpackMessage(frameRead(input), concat(seed, tokenBytes()));
            if (result.length == 0) {
                System.err.println("[Nimbus Gateway] Yggdrasil Join response failed CRC or was empty");
                return false;
            }
            if (result[0] != 0) {
                System.err.println("[Nimbus Gateway] Yggdrasil Join rejected, status=" + (result[0] & 0xFF));
                return false;
            }
            return true;
        }
    }

    private byte[] initialize(byte[] seed, byte[] signContent) throws Exception {
        byte[] token = tokenBytes();
        byte[] encryptedSeed = aes(seed, token);
        byte[] sign = sha256(concat(intBytes(authId()), encryptedSeed,
                bytes(launcherVersion), bytes("netease"), bytes(crcSalt), bytes(target.server().gameVersion()), MC_VERSION_SALT));
        byte[] client = rsa(signContent, publicKey(), Cipher.DECRYPT_MODE);
        if (client.length < 51 || !java.util.Arrays.equals(java.util.Arrays.copyOfRange(client, 19, 51), sha256(seed)))
            throw new IOException("Yggdrasil 校验和不匹配");
        byte[] signData = rsa(concat(java.util.Arrays.copyOf(client, 19), sign), privateKey(), Cipher.ENCRYPT_MODE);
        // NEL writes the protocol version with an explicitly big-endian ushort.
        byte[] payload = concat(intBytes(authId()), encryptedSeed, shortStringBigEndian(launcherVersion), byteString("netease"), TCP_SALT,
                shortBytes(signData), byteString(target.server().gameVersion()), MC_VERSION_SALT);
        return concat(new byte[]{(byte) payload.length, (byte) (payload.length >>> 8)}, payload);
    }

    private byte[] join(String serverId, byte[] seed) throws Exception {
        int time = (int) (System.currentTimeMillis() / 1000L);
        return buildJoinMessage(
                target.server().gameId(), serverId, launcherVersion, target.server().gameVersion(), time,
                crcSalt, modInfo, bootstrapMd5, datMd5, Integer.parseInt(target.account().userId()), seed
        );
    }

    static byte[] buildJoinMessage(
            String gameId,
            String serverId,
            String launcherVersion,
            String gameVersion,
            int time,
            String crcSalt,
            String modInfo,
            String bootstrapMd5,
            String datMd5,
            int userId,
            byte[] seed
    ) throws Exception {
        String hash = launcherVersion + gameVersion + time + crcSalt + modInfo + bootstrapMd5 + datMd5 + "[]";
        byte[] hashData = sha256(concat(bytes(hash), intBytes(userId), seed));
        return concat(longBytesLittle(Long.parseLong(gameId)), byteString(serverId), byteString(launcherVersion),
                byteString(gameVersion), intBytes(time), hashData, shortString(modInfo), shortString("[]"),
                shortBytes(new byte[0]), byteString("netease"));
    }

    private Endpoint endpoint() throws Exception {
        List<Endpoint> cached = endpoints;
        if (cached == null) synchronized (this) {
            if (endpoints == null) {
                String body = http.send(HttpRequest.newBuilder(AUTH_SERVER_LIST).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
                JsonArray array = JsonParser.parseString(body).getAsJsonArray();
                List<Endpoint> values = new ArrayList<>();
                for (var item : array) {
                    JsonObject server = item.getAsJsonObject();
                    String host = stringValue(server, "IP", stringValue(server, "ip", stringValue(server, "host", "")));
                    int port = intValue(server, "Port", intValue(server, "port", 0));
                    if (host.isBlank() || port < 1 || port > 65_535) {
                        throw new IOException("网易认证服务器列表包含无效地址");
                    }
                    values.add(new Endpoint(host, port));
                }
                if (values.isEmpty()) throw new IOException("网易认证服务器列表为空");
                endpoints = List.copyOf(values);
            }
            cached = endpoints;
        }
        return cached.get(ThreadLocalRandom.current().nextInt(cached.size()));
    }

    private String loadLauncherVersion() throws GatewayProtocolException {
        try {
            String value = http.send(HttpRequest.newBuilder(PATCH_LIST).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
            int comma = value.lastIndexOf(',');
            if (comma < 0) throw new IOException("版本列表格式无效");
            JsonObject object = JsonParser.parseString("{" + value.substring(0, comma) + "}").getAsJsonObject();
            String latest = ""; for (String key : object.keySet()) latest = key;
            return requireValue(latest, "白端版本");
        } catch (Exception error) { throw new GatewayProtocolException("无法获取白端版本", error); }
    }

    /**
     * NEL obtains the salt from its CRC service instead of baking a historical
     * value into the client. Keep the same precedence while exposing a Nimbus
     * override for offline/testing environments.
     */
    private String loadCrcSalt() throws GatewayProtocolException {
        String override = firstNonBlank(
                System.getProperty("nimbus.gateway.crcSalt", ""),
                System.getenv().getOrDefault("NIMBUS_GATEWAY_CRC_SALT", "")
        );
        if (!override.isBlank()) return override;
        try {
            HttpRequest request = HttpRequest.newBuilder(CRC_SALT_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + CRC_SALT_ACCESS_TOKEN)
                    .header("User-Agent", "Nimbus Gateway")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject data = root.has("data") && root.get("data").isJsonObject()
                        ? root.getAsJsonObject("data") : root;
                String value = stringValue(data, "crcSalt", stringValue(data, "salt", ""));
                if (!value.isBlank()) return value.trim();
            }
            System.err.println("[Nimbus Gateway] CRC Salt service returned HTTP " + response.statusCode());
        } catch (Exception error) {
            System.err.println("[Nimbus Gateway] CRC Salt service unavailable: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
        // Historical 1.20 fallback used by the reference clients.
        return "22AC4B0143EFFC80F2905B267D4D84D3";
    }

    private static String redact(String value) {
        if (value == null || value.length() <= 8) return value == null ? "<blank>" : value;
        return value.substring(0, 8) + "...";
    }

    private byte[] tokenBytes() { return xor(target.account().userToken().getBytes(StandardCharsets.US_ASCII), TOKEN_KEY); }
    private int authId() { return skip32((int) Long.parseUnsignedLong(target.account().userId())); }

    private static String[] md5For(String version) throws GatewayProtocolException {
        if (!"1.20".equals(version)) throw new GatewayProtocolException("不支持的白端版本: " + version);
        return new String[]{"2A7A476411A1687A56DC6848829C1AE4", "D285CBF97D9BA30D3C445DBF1C342634"};
    }

    static String modInfo(WhiteClientInstallation installation) throws GatewayProtocolException {
        JsonObject root = new JsonObject();
        JsonArray mods = new JsonArray();
        for (Path component : installation.coreComponents()) {
            try (var files = Files.walk(component)) {
                List<Path> jars = files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".jar")).toList();
                String[] identity = coreComponentIdentity(component);
                for (Path jar : jars) addMod(mods, identity[0], identity[1], md5(jar));
            } catch (IOException error) { throw new GatewayProtocolException("无法读取白端核心组件", error); }
        }
        for (Path component : installation.serverComponents()) {
            try (var files = Files.walk(component)) {
                for (Path jar : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".jar")).toList()) {
                    String name = jar.getFileName().toString();
                    int separator = name.indexOf('@');
                    String iid = separator > 0 ? name.substring(0, separator) : name;
                    addMod(mods, name, iid, md5(jar));
                }
            } catch (IOException error) { throw new GatewayProtocolException("无法读取白端组件", error); }
        }
        root.add("mods", mods); return root.toString();
    }

    private static String[] coreComponentIdentity(Path component) throws GatewayProtocolException {
        Path parent = component.getParent();
        String directory = parent == null ? "" : parent.getFileName().toString();
        int separator = directory.lastIndexOf('-');
        if (separator < 1 || separator == directory.length() - 1) {
            throw new GatewayProtocolException("白端核心组件目录无效: " + directory);
        }
        String iid = directory.substring(0, separator);
        String moduleType = directory.substring(separator + 1);
        return new String[]{iid + "@" + moduleType + "@0.jar", iid};
    }

    private static void addMod(JsonArray mods, String name, String iid, String md5) {
        JsonObject mod = new JsonObject();
        mod.addProperty("modPath", name);
        mod.addProperty("name", "");
        mod.addProperty("id", name);
        mod.addProperty("iid", iid);
        mod.addProperty("md5", md5.toUpperCase());
        mod.addProperty("version", "");
        mods.add(mod);
    }

    private static byte[] chacha(byte[] input, byte[] key, boolean encrypt) {
        byte[] output = new byte[input.length]; byte[] state = new byte[64]; long counter = 0;
        for (int offset = 0; offset < input.length; offset += 64) { byte[] block = chachaBlock(key, NONCE, counter++); for (int i = 0; i < Math.min(64, input.length - offset); i++) output[offset + i] = (byte) (input[offset + i] ^ block[i]); }
        return output;
    }

    private static byte[] packMessage(int type, byte[] data, byte[] key) {
        byte[] message = new byte[data.length + 10];
        putLe16(message, 0, message.length - 2);
        message[6] = (byte) type; message[7] = (byte) 136; message[8] = (byte) 136; message[9] = (byte) 136;
        System.arraycopy(data, 0, message, 10, data.length);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(message, 6, message.length - 6);
        putLe32(message, 2, (int) crc.getValue());
        byte[] encrypted = chacha(java.util.Arrays.copyOfRange(message, 2, message.length), key, true);
        System.arraycopy(encrypted, 0, message, 2, encrypted.length);
        return message;
    }

    private static byte[] unpackMessage(byte[] data, byte[] key) {
        byte[] decoded = chacha(data, key, false);
        if (decoded.length < 8) return new byte[0];
        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(decoded, 4, decoded.length - 4);
        if ((int) crc.getValue() != le(decoded, 0) || decoded[4] != 9) return new byte[0];
        return java.util.Arrays.copyOfRange(decoded, 8, decoded.length);
    }

    private static byte[] chachaBlock(byte[] key, byte[] nonce, long counter) {
        int[] x = new int[16]; x[0]=0x61707865; x[1]=0x3320646e; x[2]=0x79622d32; x[3]=0x6b206574; for(int i=0;i<8;i++) x[4+i]=le(key,i*4); x[12]=(int)counter; x[13]=le(nonce,0); x[14]=le(nonce,4); x[15]=le(nonce,8); int[] w=x.clone(); for(int i=0;i<4;i++){ qr(w,0,4,8,12);qr(w,1,5,9,13);qr(w,2,6,10,14);qr(w,3,7,11,15);qr(w,0,5,10,15);qr(w,1,6,11,12);qr(w,2,7,8,13);qr(w,3,4,9,14);} byte[] out=new byte[64]; for(int i=0;i<16;i++) putLe(out,i*4,w[i]+x[i]); return out;
    }
    private static void putLe(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    private static void qr(int[] x,int a,int b,int c,int d){x[a]+=x[b];x[d]=Integer.rotateLeft(x[d]^x[a],16);x[c]+=x[d];x[b]=Integer.rotateLeft(x[b]^x[c],12);x[a]+=x[b];x[d]=Integer.rotateLeft(x[d]^x[a],8);x[c]+=x[d];x[b]=Integer.rotateLeft(x[b]^x[c],7);}
    private static byte[] aes(byte[] value, byte[] key) throws Exception { Cipher c=Cipher.getInstance("AES/ECB/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES")); return c.doFinal(value); }
    private static byte[] rsa(byte[] value, java.security.Key key, int mode) throws Exception { Cipher c=Cipher.getInstance("RSA/ECB/PKCS1Padding"); c.init(mode,key); return c.doFinal(value); }
    private static PublicKey publicKey() throws Exception { return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY))); }
    private static PrivateKey privateKey() throws Exception { return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY))); }
    private static byte[] frameRead(InputStream in) throws IOException { int a=in.read(),b=in.read(); if((a|b)<0)throw new EOFException(); int n=a|(b<<8); byte[] v=in.readNBytes(n); if(v.length!=n)throw new EOFException(); return v; }
    /** Writes a protocol frame; the value already contains its little-endian length prefix. */
    private static void frameWrite(OutputStream out, byte[] value) throws IOException { out.write(value);out.flush(); }
    private static byte[] string(String s){byte[] b=bytes(s);return concat(new byte[]{(byte)b.length},b);}
    /** NEL WriteShortString default: little-endian ushort length. */
    private static byte[] shortString(String s){byte[] b=bytes(s);return concat(new byte[]{(byte)b.length,(byte)(b.length>>>8)},b);}
    /** InitializeMessage's protocol version uses WriteShortString(..., false). */
    private static byte[] shortStringBigEndian(String s){byte[] b=bytes(s);return concat(new byte[]{(byte)(b.length>>>8),(byte)b.length},b);}
    /** NEL WriteShortBytes default: little-endian ushort length. */
    private static byte[] shortBytes(byte[] b){return concat(new byte[]{(byte)b.length,(byte)(b.length>>>8)},b);}
    private static byte[] byteString(String s){byte[] b=bytes(s);return concat(new byte[]{(byte)b.length},b);}
    private static byte[] intBytes(int n){return new byte[]{(byte)n,(byte)(n>>>8),(byte)(n>>>16),(byte)(n>>>24)};}
    private static byte[] longBytesLittle(long n){byte[] b=new byte[8];for(int i=0;i<8;i++)b[i]=(byte)(n>>>(i*8));return b;}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] concat(byte[]... values){int n=0;for(byte[] v:values)n+=v.length;byte[] r=new byte[n];int p=0;for(byte[] v:values){System.arraycopy(v,0,r,p,v.length);p+=v.length;}return r;}
    private static byte[] xor(byte[] a,byte[] b){byte[] r=new byte[a.length];for(int i=0;i<a.length;i++)r[i]=(byte)(a[i]^b[i%b.length]);return r;}
    private static byte[] sha256(byte[] v)throws Exception{return java.security.MessageDigest.getInstance("SHA-256").digest(v);}
    private static String stringValue(JsonObject object, String name, String fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) return fallback;
        try { return object.get(name).getAsString(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static int intValue(JsonObject object, String name, int fallback) {
        if (!object.has(name) || object.get(name).isJsonNull()) return fallback;
        try { return object.get(name).getAsInt(); } catch (RuntimeException ignored) { return fallback; }
    }
    private static String md5(Path file) throws IOException { try { var d=java.security.MessageDigest.getInstance("MD5"); try(var in=Files.newInputStream(file)){byte[] b=new byte[65536]; int n; while((n=in.read(b))>0)d.update(b,0,n);} return HexFormat.of().formatHex(d.digest()).toUpperCase(); } catch (java.security.NoSuchAlgorithmException e){throw new IOException(e);} }
    private static int le(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24);}
    private static int skip32(int value){return GatewayLaunchCrypto.encryptSkip32(value);}
    private static void putLe16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
    private static void putLe32(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    private static String requireValue(String value,String name)throws GatewayProtocolException{if(value==null||value.isBlank())throw new GatewayProtocolException(name+" 未配置，请设置 -Dnimbus.gateway.crcSalt");return value.trim();}
    private static String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value.trim(); return ""; }
    private record Endpoint(String host,int port){}
}
