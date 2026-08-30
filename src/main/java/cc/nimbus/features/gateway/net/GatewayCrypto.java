package cc.nimbus.features.gateway.net;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class GatewayCrypto {
    private static final byte[] HTTP_IV = "szkgpbyimxavqjcn".getBytes(StandardCharsets.US_ASCII);
    private static final String[] HTTP_KEY_TEXT = (
            "MK6mipwmOUedplb6,OtEylfId6dyhrfdn,VNbhn5mvUaQaeOo9,bIEoQGQYjKd02U0J," +
            "fuaJrPwaH2cfXXLP,LEkdyiroouKQ4XN1,jM1h27H4UROu427W,DhReQada7gZybTDk," +
            "ZGXfpSTYUvcdKqdY,AZwKf7MWZrJpGR5W,amuvbcHw38TcSyPU,SI4QotspbjhyFdT0," +
            "VP4dhjKnDGlSJtbB,UXDZx4KhZywQ2tcn,NIK73ZNvNqzva4kd,WeiW7qU766Q1YQZI"
    ).split(",");
    private static final byte[][] HTTP_KEYS = Arrays.stream(HTTP_KEY_TEXT)
            .map(value -> value.getBytes(StandardCharsets.US_ASCII))
            .toArray(byte[][]::new);
    private static final SecureRandom RANDOM = new SecureRandom();

    private GatewayCrypto() {
    }

    static String md5Hex(String value) throws GatewayProtocolException {
        return md5Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    static String md5Hex(byte[] value) throws GatewayProtocolException {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new GatewayProtocolException("MD5 is unavailable", error);
        }
    }

    static String encryptMpayParameters(String json, String deviceKey) throws GatewayProtocolException {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeHex(deviceKey), "AES"));
            return hex(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new GatewayProtocolException("Unable to encrypt MPay parameters", error);
        }
    }

    static byte[] encryptAuthentication(byte[] plain) throws GatewayProtocolException {
        int paddedLength = ((plain.length + HTTP_IV.length + 15) / 16) * 16;
        byte[] body = new byte[paddedLength];
        System.arraycopy(plain, 0, body, 0, plain.length);
        System.arraycopy(HTTP_IV, 0, body, plain.length, HTTP_IV.length);
        int keySlot = RANDOM.nextInt(HTTP_KEYS.length - 1);
        byte keyIndex = (byte) ((keySlot << 4) | 2);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(HTTP_KEYS[keySlot], "AES"),
                    new IvParameterSpec(HTTP_IV));
            byte[] encrypted = cipher.doFinal(body);
            ByteArrayOutputStream output = new ByteArrayOutputStream(HTTP_IV.length + encrypted.length + 1);
            output.writeBytes(HTTP_IV);
            output.writeBytes(encrypted);
            output.write(keyIndex);
            return output.toByteArray();
        } catch (GeneralSecurityException error) {
            throw new GatewayProtocolException("Unable to encrypt X19 authentication", error);
        }
    }

    static byte[] decryptAuthentication(byte[] body) throws GatewayProtocolException {
        if (body.length < 18) throw new GatewayProtocolException("X19 authentication response is truncated");
        int keySlot = (body[body.length - 1] >>> 4) & 0x0F;
        if (keySlot >= HTTP_KEYS.length) throw new GatewayProtocolException("X19 authentication key is invalid");
        byte[] iv = Arrays.copyOfRange(body, 0, 16);
        byte[] encrypted = Arrays.copyOfRange(body, 16, body.length - 1);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(HTTP_KEYS[keySlot], "AES"),
                    new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            // Match NEL HttpCipher.HttpDecrypt exactly. The response trailer is sixteen
            // non-zero bytes followed by zero padding; it is not guaranteed to equal
            // the request-side IV literal.
            int trailerBytes = 0;
            int end = decrypted.length - 1;
            while (trailerBytes < HTTP_IV.length) {
                if (end < 0) throw new GatewayProtocolException("X19 authentication trailer is missing");
                if (decrypted[end--] != 0) trailerBytes++;
            }
            return Arrays.copyOf(decrypted, end + 1);
        } catch (GeneralSecurityException error) {
            throw new GatewayProtocolException("Unable to decrypt X19 authentication", error);
        }
    }

    static Map<String, String> dynamicHeaders(String path, String body, String userId, String userToken)
            throws GatewayProtocolException {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.writeBytes(md5Hex(userToken).getBytes(StandardCharsets.UTF_8));
        input.writeBytes(body.getBytes(StandardCharsets.UTF_8));
        input.writeBytes("0eGsBkhl".getBytes(StandardCharsets.UTF_8));
        input.writeBytes(normalizedPath.getBytes(StandardCharsets.UTF_8));

        String secretMd5 = md5Hex(input.toByteArray());
        StringBuilder binary = new StringBuilder(secretMd5.length() * 8);
        for (int index = 0; index < secretMd5.length(); index++) {
            String value = Integer.toBinaryString(secretMd5.charAt(index));
            binary.append("0".repeat(Math.max(0, 8 - value.length()))).append(value);
        }
        String rotated = binary.substring(6) + binary.substring(0, 6);
        byte[] httpToken = secretMd5.getBytes(StandardCharsets.UTF_8);
        for (int blockIndex = 0; blockIndex < rotated.length() / 8; blockIndex++) {
            int xor = 0;
            int start = blockIndex * 8;
            for (int bit = 0; bit < 8; bit++) {
                if (rotated.charAt(start + 7 - bit) == '1') xor |= 1 << bit;
            }
            httpToken[blockIndex] ^= (byte) xor;
        }

        String token = (Base64.getEncoder().encodeToString(Arrays.copyOf(httpToken, 12)) + "1")
                .replace('+', 'm')
                .replace('/', 'o');
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("user-id", userId);
        headers.put("user-token", token);
        return headers;
    }

    private static byte[] decodeHex(String value) {
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("hex value has odd length");
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("invalid hex value");
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xFF));
        return result.toString();
    }
}
