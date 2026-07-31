package cc.nimbus.utils.render.skija.internal.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ResourceHelper {
    private ResourceHelper() {
    }

    public static byte[] classpath(String resource) {
        try (InputStream input = ResourceHelper.class.getClassLoader().getResourceAsStream(resource)) {
            if (input != null) return input.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resource, e);
        }

        String payloadPath = System.getProperty("nimbus.client.payload_path");
        if (payloadPath == null || payloadPath.isBlank()) {
            throw new IllegalArgumentException("Resource not found: " + resource);
        }
        try (JarFile jar = new JarFile(payloadPath)) {
            JarEntry entry = jar.getJarEntry(resource);
            if (entry == null) throw new IllegalArgumentException("Resource not found: " + resource);
            try (InputStream input = jar.getInputStream(entry)) {
                return input.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource from payload: " + resource, e);
        }
    }
}
