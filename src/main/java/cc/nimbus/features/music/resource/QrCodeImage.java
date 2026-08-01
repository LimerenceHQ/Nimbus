package cc.nimbus.features.music.resource;

import cc.nimbus.features.music.internal.qr.QrCode;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public final class QrCodeImage {
    private QrCodeImage() {}

    public static byte[] png(String text, int size) {
        if (text == null || text.isBlank()) return new byte[0];
        try {
            int imageSize = Math.max(64, Math.min(1024, size));
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
            int border = 4;
            int scale = Math.max(1, imageSize / (qr.size + border * 2));
            int actual = (qr.size + border * 2) * scale;
            int offset = (imageSize - actual) / 2;
            BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, imageSize, imageSize);
                graphics.setColor(Color.BLACK);
                for (int y = 0; y < qr.size; y++) {
                    for (int x = 0; x < qr.size; x++) {
                        if (qr.getModule(x, y)) graphics.fillRect(offset + (x + border) * scale,
                                offset + (y + border) * scale, scale, scale);
                    }
                }
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to generate login QR code", error);
        }
    }
}
