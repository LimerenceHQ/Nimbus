package cc.nimbus.features.gateway.launch;

import cc.nimbus.features.gateway.net.GatewayProtocolException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

final class GatewayLaunchCrypto {
    private static final int[] F_TABLE = {
            163, 215, 9, 131, 248, 72, 246, 244, 179, 33, 21, 120, 153, 177, 175, 249,
            231, 45, 77, 138, 206, 76, 202, 46, 82, 149, 217, 30, 78, 56, 68, 40,
            10, 223, 2, 160, 23, 241, 96, 104, 18, 183, 122, 195, 233, 250, 61, 83,
            150, 132, 107, 186, 242, 99, 154, 25, 124, 174, 229, 245, 247, 22, 106, 162,
            57, 182, 123, 15, 193, 147, 129, 27, 238, 180, 26, 234, 208, 145, 47, 184,
            85, 185, 218, 133, 63, 65, 191, 224, 90, 88, 128, 95, 102, 11, 216, 144,
            53, 213, 192, 167, 51, 6, 101, 105, 69, 0, 148, 86, 109, 152, 155, 118,
            151, 252, 178, 194, 176, 254, 219, 32, 225, 235, 214, 228, 221, 71, 74, 29,
            66, 237, 158, 110, 73, 60, 205, 67, 39, 210, 7, 212, 222, 199, 103, 24,
            137, 203, 48, 31, 141, 198, 143, 170, 200, 116, 220, 201, 93, 92, 49, 164,
            112, 136, 97, 44, 159, 13, 43, 135, 80, 130, 84, 100, 38, 125, 3, 64,
            52, 75, 28, 115, 209, 196, 253, 59, 204, 251, 127, 171, 230, 62, 91, 165,
            173, 4, 35, 156, 20, 81, 34, 240, 41, 121, 113, 126, 255, 140, 14, 226,
            12, 239, 188, 114, 117, 111, 55, 161, 236, 211, 142, 98, 139, 134, 16, 232,
            8, 119, 17, 190, 146, 79, 36, 197, 50, 54, 157, 207, 243, 166, 187, 172,
            94, 108, 169, 19, 87, 37, 181, 227, 189, 168, 58, 1, 5, 89, 42, 70
    };
    private static final byte[] SKIP32_KEY = "SaintSteve".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TOKEN_KEY = "debbde3548928fab".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TOKEN_IV = "afd4c5c5a7c456a1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private GatewayLaunchCrypto() {
    }

    static String roleUuid(String roleName, String userId) throws GatewayProtocolException {
        long parsed;
        try {
            parsed = Long.parseUnsignedLong(userId);
        } catch (NumberFormatException error) {
            throw new GatewayProtocolException("网易用户 ID 无效", error);
        }
        if (Long.compareUnsigned(parsed, 0xFFFF_FFFFL) > 0) {
            throw new GatewayProtocolException("网易用户 ID 超出白端 UUID 范围");
        }
        try {
            byte[] uuid = MessageDigest.getInstance("MD5").digest(roleName.getBytes(StandardCharsets.UTF_8));
            int encrypted = encryptSkip32((int) parsed);
            uuid[12] = (byte) encrypted;
            uuid[13] = (byte) (encrypted >>> 8);
            uuid[14] = (byte) (encrypted >>> 16);
            uuid[15] = (byte) (encrypted >>> 24);
            uuid[6] = (byte) ((uuid[6] & 0x0F) | 0x40);
            uuid[8] = (byte) ((uuid[8] & 0x3F) | 0x80);
            return hex(uuid, false);
        } catch (NoSuchAlgorithmException error) {
            throw new GatewayProtocolException("MD5 is unavailable", error);
        }
    }

    static String encryptedLauncherToken(String userToken) throws GatewayProtocolException {
        String value = randomText(8) + userToken + randomText(8);
        byte[] input = value.getBytes(StandardCharsets.US_ASCII);
        byte[] padded = new byte[((input.length + 15) / 16) * 16];
        System.arraycopy(input, 0, padded, 0, input.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TOKEN_KEY, "AES"), new IvParameterSpec(TOKEN_IV));
            return hex(cipher.doFinal(padded), true);
        } catch (GeneralSecurityException error) {
            throw new GatewayProtocolException("无法生成白端启动 Token", error);
        }
    }

    static int encryptSkip32(int value) {
        int[] buffer = {
                (value >>> 24) & 0xFF,
                (value >>> 16) & 0xFF,
                (value >>> 8) & 0xFF,
                value & 0xFF
        };
        int round = 0;
        int left = (buffer[0] << 8) | buffer[1];
        int right = (buffer[2] << 8) | buffer[3];
        for (int index = 0; index < 12; index++) {
            right ^= g(round, left) ^ round;
            int secondRound = round + 1;
            left ^= g(secondRound, right) ^ secondRound;
            round = secondRound + 1;
        }
        return ((right >>> 8) << 24)
                | ((right & 0xFF) << 16)
                | ((left >>> 8) << 8)
                | (left & 0xFF);
    }

    private static int g(int round, int value) {
        int high = value >>> 8;
        int low = value & 0xFF;
        int g1 = F_TABLE[low ^ (SKIP32_KEY[(4 * round) % 10] & 0xFF)] ^ high;
        int g2 = F_TABLE[g1 ^ (SKIP32_KEY[(4 * round + 1) % 10] & 0xFF)] ^ low;
        int g3 = F_TABLE[g2 ^ (SKIP32_KEY[(4 * round + 2) % 10] & 0xFF)] ^ g1;
        int g4 = F_TABLE[g3 ^ (SKIP32_KEY[(4 * round + 3) % 10] & 0xFF)] ^ g2;
        return (g3 << 8) | g4;
    }

    private static String randomText(int length) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return result.toString();
    }

    private static String hex(byte[] value, boolean upper) {
        char[] alphabet = (upper ? "0123456789ABCDEF" : "0123456789abcdef").toCharArray();
        char[] result = new char[value.length * 2];
        for (int index = 0; index < value.length; index++) {
            int item = value[index] & 0xFF;
            result[index * 2] = alphabet[item >>> 4];
            result[index * 2 + 1] = alphabet[item & 0x0F];
        }
        return new String(result);
    }
}
