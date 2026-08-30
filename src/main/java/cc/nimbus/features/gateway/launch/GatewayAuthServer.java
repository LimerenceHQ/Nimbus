package cc.nimbus.features.gateway.launch;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Loopback AuthSock expected by the NetEase white client native authentication library. */
public final class GatewayAuthServer implements AutoCloseable {
    private static final int MAX_FIELD_BYTES = 1024 * 1024;

    private final String expectedGameId;
    private final String expectedUserId;
    private final GatewayJoinAuthenticator authenticator;
    private final ExecutorService workers = Executors.newCachedThreadPool(new AuthThreadFactory());
    private final ServerSocket listener;
    private volatile boolean closed;

    private GatewayAuthServer(
            String expectedGameId,
            String expectedUserId,
            GatewayJoinAuthenticator authenticator,
            ServerSocket listener
    ) {
        this.expectedGameId = expectedGameId;
        this.expectedUserId = expectedUserId;
        this.authenticator = authenticator;
        this.listener = listener;
        workers.execute(this::acceptLoop);
    }

    public static GatewayAuthServer start(
            String expectedGameId,
            String expectedUserId,
            GatewayJoinAuthenticator authenticator
    ) throws IOException {
        String gameId = requireValue(expectedGameId, "expectedGameId");
        String userId = requireValue(expectedUserId, "expectedUserId");
        ServerSocket listener = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        return new GatewayAuthServer(gameId, userId, Objects.requireNonNull(authenticator, "authenticator"), listener);
    }

    public int port() {
        return listener.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket client = listener.accept();
                workers.execute(() -> handle(client));
            } catch (IOException error) {
                if (!closed) System.err.println("[Nimbus Gateway] AuthSock accept failed: " + message(error));
                return;
            }
        }
    }

    private void handle(Socket client) {
        try (client; InputStream input = client.getInputStream(); OutputStream output = client.getOutputStream()) {
            client.setSoTimeout(30_000);
            System.out.println("[Nimbus Gateway] AuthSock accepted: " + client.getRemoteSocketAddress());
            int responseCode = 1;
            try {
                String gameId = readString(input);
                String userId = readString(input);
                String serverId = readString(input);
                // NEL passes the native callback straight to its authenticator.
                // The game id is advisory here: the launcher session already
                // binds the selected game/account, and some white-client builds
                // send a legacy or empty game id during the callback.
                System.out.println("[Nimbus Gateway] AuthSock request: gameId="
                        + printable(gameId) + ", userId=" + printable(userId)
                        + ", serverId=" + printable(serverId));
                boolean success = !serverId.isBlank() && authenticator.authenticate(serverId);
                if (success) {
                    responseCode = 0;
                    System.out.println("[Nimbus Gateway] AuthSock NetEase join succeeded");
                } else {
                    System.err.println("[Nimbus Gateway] AuthSock NetEase join was rejected");
                }
            } catch (Exception error) {
                if (!closed) System.err.println("[Nimbus Gateway] AuthSock request failed: " + message(error));
            }
            writeInt32LittleEndian(output, responseCode);
            output.flush();
        } catch (IOException error) {
            if (!closed) System.err.println("[Nimbus Gateway] AuthSock response failed: " + message(error));
        }
    }

    private static String readString(InputStream input) throws IOException {
        int length = readInt32LittleEndian(input);
        if (length < 0 || length > MAX_FIELD_BYTES) throw new IOException("AuthSock field length is invalid");
        byte[] value = input.readNBytes(length);
        if (value.length != length) throw new EOFException("AuthSock field is truncated");
        return new String(value, StandardCharsets.UTF_8);
    }

    private static int readInt32LittleEndian(InputStream input) throws IOException {
        int b0 = input.read();
        int b1 = input.read();
        int b2 = input.read();
        int b3 = input.read();
        if ((b0 | b1 | b2 | b3) < 0) throw new EOFException();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void writeInt32LittleEndian(OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String requireValue(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return result;
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static String printable(String value) {
        if (value == null || value.isBlank()) return "<blank>";
        return value.length() > 128 ? value.substring(0, 128) + "..." : value;
    }

    private static final class AuthThreadFactory implements ThreadFactory {
        private int index;

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "nimbus-gateway-auth-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
