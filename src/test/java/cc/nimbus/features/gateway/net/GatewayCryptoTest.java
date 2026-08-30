package cc.nimbus.features.gateway.net;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class GatewayCryptoTest {
    @Test
    void decryptsNelResponseTrailerWithoutAssumingRequestIvMarker() throws Exception {
        byte[] plain = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
        byte[] trailer = "abcdefghijklmnop".getBytes(StandardCharsets.US_ASCII);
        byte[] decrypted = new byte[32];
        System.arraycopy(plain, 0, decrypted, 0, plain.length);
        System.arraycopy(trailer, 0, decrypted, plain.length, trailer.length);

        byte[] iv = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] key = "MK6mipwmOUedplb6".getBytes(StandardCharsets.US_ASCII);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(decrypted);
        byte[] response = new byte[iv.length + encrypted.length + 1];
        System.arraycopy(iv, 0, response, 0, iv.length);
        System.arraycopy(encrypted, 0, response, iv.length, encrypted.length);
        response[response.length - 1] = 2;

        assertArrayEquals(plain, GatewayCrypto.decryptAuthentication(response));
    }
}
