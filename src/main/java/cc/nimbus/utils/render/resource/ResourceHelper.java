package cc.nimbus.utils.render.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;

public class ResourceHelper {
    public static byte[] classpath(String resource) {
        try {
            InputStream input = ResourceHelper.class.getClassLoader().getResourceAsStream(resource);
            if (input == null) {
                String payloadPath = System.getProperty("nimbus.client.payload_path");
                if (payloadPath != null) {
                    try {
                        File jarFile = new File(payloadPath);
                        if (jarFile.exists()) {
                            URL url = new URL("jar:file:" + jarFile.getAbsolutePath().replace("\\", "/") + "!/" + resource);
                            input = url.openStream();
                        }
                    } catch (Exception e) {
                        System.err.println("[ResourceHelper] Failed to load from payload jar: " + e.getMessage());
                    }
                } else {
                    throw new IllegalArgumentException("Resource not found: " + resource);
                }
            }
            if (input != null) {
                byte[] bytes = input.readAllBytes();
                input.close();
                return bytes;
            } else {
                throw new IllegalArgumentException("Resource not found: " + resource);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resource, e);
        }
    }

    public static byte[] runtime(String resource) {
        String currentDir = System.getProperty("user.dir");
        File file = new File(currentDir + File.separator + resource);
        if (file.exists()) {
            try (InputStream input = Files.newInputStream(file.toPath())) {
                return input.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load resource: " + resource, e);
            }
        } else {
            throw new RuntimeException("Resource not found: " + resource);
        }
    }

    public static byte[] url(String resource) {
        boolean isHttp_s = resource.startsWith("https://") || resource.startsWith("http://");
        if (!isHttp_s) {
            resource = "https://" + resource;
        }
        try {
            URL url = new URL(resource);
            URLConnection con = url.openConnection();
            try (InputStream inputStream = con.getInputStream();) {
                return inputStream.readAllBytes();
            }
        } catch (IOException e) {
            Exception exception = new RuntimeException("Failed to download resource: " + resource, e);
            exception.printStackTrace();
        }
        return null;
    }
}
