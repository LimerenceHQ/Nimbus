package cc.nimbus.features.gateway.launch;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** NetEase white-client launcher RPC endpoint bound exclusively to loopback. */
public final class GatewayRpcServer implements AutoCloseable {
    private static final int MAX_PENDING_MESSAGES = 64;

    private final GatewayLaunchTarget target;
    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newCachedThreadPool(new RpcThreadFactory());
    private final Object sendLock = new Object();
    private final ArrayDeque<byte[]> pending = new ArrayDeque<>();
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private final CompletableFuture<Void> launchReady = new CompletableFuture<>();

    private Socket client;
    private OutputStream output;
    private volatile boolean closed;

    private GatewayRpcServer(GatewayLaunchTarget target, ServerSocket listener) {
        this.target = target;
        this.listener = listener;
        workers.execute(this::acceptLoop);
    }

    public static GatewayRpcServer start(GatewayLaunchTarget target) throws IOException {
        ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
        return new GatewayRpcServer(Objects.requireNonNull(target, "target"), listener);
    }

    public int port() {
        return listener.getLocalPort();
    }

    public CompletableFuture<Void> connected() {
        return connected;
    }

    public CompletableFuture<Void> launchReady() {
        return launchReady;
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket accepted = listener.accept();
                accepted.setSoTimeout(0);
                synchronized (sendLock) {
                    closeClientLocked();
                    client = accepted;
                    output = accepted.getOutputStream();
                    while (!pending.isEmpty()) writeFrameLocked(pending.removeFirst());
                }
                connected.complete(null);
                workers.execute(() -> receiveLoop(accepted));
            } catch (IOException error) {
                if (!closed) fail("RPC accept failed", error);
                return;
            }
        }
    }

    private void receiveLoop(Socket accepted) {
        try (InputStream input = accepted.getInputStream()) {
            while (!closed && !accepted.isClosed()) {
                int length = readUnsignedShort(input);
                if (length < 2) throw new IOException("RPC message is too short");
                byte[] message = input.readNBytes(length);
                if (message.length != length) throw new EOFException("RPC message is truncated");
                int messageId = unsignedShort(message, 0);
                System.out.println("[Nimbus Gateway] RPC request id=" + messageId
                        + ", payload=" + (message.length - 2));
                handle(messageId, message, 2);
            }
        } catch (EOFException ignored) {
        } catch (IOException | RuntimeException error) {
            if (!closed) fail("RPC receive failed", error);
        } finally {
            synchronized (sendLock) {
                if (client == accepted) closeClientLocked();
            }
        }
    }

    private void handle(int messageId, byte[] message, int payloadOffset) throws IOException {
        switch (messageId) {
            case 18 -> handlePlayerMessage(message, payloadOffset);
            case 19, 4612 -> {
                // The official launcher records these events but sends no reply.
            }
            case 261 -> {
                launchReady.complete(null);
                System.out.println("[Nimbus Gateway] RPC launch-ready (261), sending 1799");
                send(new MessageWriter(1799)
                        .string(target.server().serverHost())
                        .int32(target.server().serverPort())
                        .string(target.role().name())
                        .finish());
            }
            case 512 -> send(new MessageWriter(512).string("i'am wpflauncher").finish());
            case 517 -> send(new MessageWriter(1031)
                    .string(target.server().serverHost())
                    .int32(target.server().serverPort())
                    .string(target.role().name())
                    .bool(false)
                    .finish());
            case 520 -> handleSkinRequest(message, payloadOffset);
            case 1298 -> send(new MessageWriter(1298).bool(false).int64(0).int64(0).finish());
            default -> {
                // Unknown launcher RPC messages are forward-compatible and intentionally ignored.
            }
        }
    }

    private void handlePlayerMessage(byte[] message, int offset) throws IOException {
        if (message.length - offset < 4) return;
        int declaredLength = int32(message, offset);
        offset += 4;
        String value = "";
        if (message.length - offset >= 2) {
            int length = unsignedShort(message, offset);
            offset += 2;
            if (length <= message.length - offset) value = new String(message, offset, length, StandardCharsets.UTF_8);
        } else if (declaredLength > 0 && declaredLength <= message.length - offset) {
            value = new String(message, offset, declaredLength, StandardCharsets.UTF_8);
        }
        send(new MessageWriter(18).int32(declaredLength).string(value).finish());
    }

    private void handleSkinRequest(byte[] message, int offset) throws IOException {
        if (message.length - offset < 4) return;
        offset += 2; // entity id
        int nameLength = unsignedShort(message, offset);
        offset += 2;
        if (nameLength > message.length - offset) return;
        String name = new String(message, offset, nameLength, StandardCharsets.UTF_8);
        // An empty path tells the white client to use the default skin. Remote skin
        // retrieval is deliberately a later provider and is not required for authentication.
        send(new MessageWriter(520).string(name).string("").string("").int32(0).finish());
    }

    private void send(byte[] message) throws IOException {
        synchronized (sendLock) {
            if (output == null) {
                if (pending.size() == MAX_PENDING_MESSAGES) pending.removeFirst();
                pending.addLast(message);
                return;
            }
            writeFrameLocked(message);
        }
    }

    private void writeFrameLocked(byte[] message) throws IOException {
        if (message.length > 65_535) throw new IOException("RPC response is too large");
        output.write(message.length & 0xFF);
        output.write((message.length >>> 8) & 0xFF);
        output.write(message);
        output.flush();
    }

    private void closeClientLocked() {
        output = null;
        if (client == null) return;
        try {
            client.close();
        } catch (IOException ignored) {
        }
        client = null;
    }

    private void fail(String operation, Throwable error) {
        String detail = operation + ": " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        System.err.println("[Nimbus Gateway] " + detail);
        IOException failure = new IOException(detail, error);
        connected.completeExceptionally(failure);
        launchReady.completeExceptionally(failure);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        synchronized (sendLock) {
            closeClientLocked();
            pending.clear();
        }
        IOException failure = new IOException("Gateway RPC closed");
        connected.completeExceptionally(failure);
        launchReady.completeExceptionally(failure);
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static int readUnsignedShort(InputStream input) throws IOException {
        int low = input.read();
        int high = input.read();
        if ((low | high) < 0) throw new EOFException();
        return low | (high << 8);
    }

    private static int unsignedShort(byte[] value, int offset) {
        return (value[offset] & 0xFF) | ((value[offset + 1] & 0xFF) << 8);
    }

    private static int int32(byte[] value, int offset) {
        return (value[offset] & 0xFF)
                | ((value[offset + 1] & 0xFF) << 8)
                | ((value[offset + 2] & 0xFF) << 16)
                | ((value[offset + 3] & 0xFF) << 24);
    }

    private static final class MessageWriter {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private MessageWriter(int messageId) {
            unsignedShort(messageId);
        }

        private MessageWriter bool(boolean value) {
            output.write(value ? 1 : 0);
            return this;
        }

        private MessageWriter int32(int value) {
            output.write(value & 0xFF);
            output.write((value >>> 8) & 0xFF);
            output.write((value >>> 16) & 0xFF);
            output.write((value >>> 24) & 0xFF);
            return this;
        }

        private MessageWriter int64(long value) {
            for (int shift = 0; shift < 64; shift += 8) output.write((int) (value >>> shift) & 0xFF);
            return this;
        }

        private MessageWriter string(String value) throws IOException {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > 65_535) throw new IOException("RPC string is too long");
            unsignedShort(encoded.length);
            output.writeBytes(encoded);
            return this;
        }

        private void unsignedShort(int value) {
            output.write(value & 0xFF);
            output.write((value >>> 8) & 0xFF);
        }

        private byte[] finish() {
            return output.toByteArray();
        }
    }

    private static final class RpcThreadFactory implements ThreadFactory {
        private int index;

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "nimbus-gateway-rpc-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
