package cc.nimbus.features.music.internal.net;

import com.google.gson.Gson;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

final class NeteaseCrypto {
    private static final String IV = "0102030405060708";
    private static final String WEAPI_KEY = "0CoJUm6Qyw8W8jud";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();

    private NeteaseCrypto() {}

    static Weapi weapi(Object data) {
        String secret = random(16);
        String once = aes(GSON.toJson(data), "CBC", WEAPI_KEY, IV, false);
        return new Weapi(aes(once, "CBC", secret, IV, false), rsa(new StringBuilder(secret).reverse().toString()));
    }

    static String eapi(String uri, Object data) {
        String json = data instanceof String value ? value : GSON.toJson(data);
        String digest = md5("nobody" + uri + "use" + json + "md5forencrypt");
        return aes(uri + "-36cd479b6b5-" + json + "-36cd479b6b5-" + digest,
                "ECB", EAPI_KEY, "", true);
    }

    private static String aes(String text, String mode, String key, String iv, boolean hex) {
        try {
            Cipher cipher = Cipher.getInstance("AES/" + mode + "/PKCS5Padding");
            SecretKeySpec secret = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            if ("CBC".equals(mode)) cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
            else cipher.init(Cipher.ENCRYPT_MODE, secret);
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return hex ? HexFormat.of().withUpperCase().formatHex(encrypted) : Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encrypt Netease request", error);
        }
    }

    private static String rsa(String value) {
        try {
            byte[] key = Base64.getDecoder().decode(PUBLIC_KEY);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return HexFormat.of().formatHex(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encrypt Netease key", error);
        }
    }

    private static String md5(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("MD5 is unavailable", error);
        }
    }

    private static String random(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) value.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        return value.toString();
    }

    record Weapi(String params, String encSecKey) {}
}
