package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.PacketEvent;
import cc.nimbus.features.packet.PacketDirection;
import cc.nimbus.utils.network.ConcurrentPacketQueue;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PacketManager implements InstanceManager {
    public static final int DEFAULT_CAPACITY = 131_072;

    private final Minecraft minecraft;
    private final ConcurrentPacketQueue inbound;
    private final ConcurrentPacketQueue outbound;
    private volatile boolean inboundFrozen;
    private volatile boolean outboundFrozen;
    private int internalTimer;
    private boolean capturedThisCycle;
    private final ThreadLocal<Packet<?>> inboundHookPacket = new ThreadLocal<>();

    public PacketManager(Minecraft minecraft) {
        this(minecraft, DEFAULT_CAPACITY);
    }

    public PacketManager(Minecraft minecraft, int capacity) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        if (capacity < 2) throw new IllegalArgumentException("capacity must be at least 2");
        this.inbound = new ConcurrentPacketQueue(capacity);
        this.outbound = new ConcurrentPacketQueue(capacity);
    }

    @Override
    public void init() {
        cleanup();
        EventBus.register(this);
    }

    @Override
    public void shutdown() {
        EventBus.unregister(this);
        cleanup();
    }

    public void freeze(PacketDirection direction) {
        switch (direction) {
            case INBOUND -> inboundFrozen = true;
            case OUTBOUND -> outboundFrozen = true;
            case BOTH -> {
                inboundFrozen = true;
                outboundFrozen = true;
            }
        }
    }

    public void dispatch(PacketDirection direction) {
        switch (direction) {
            case INBOUND -> {
                List<Packet<?>> packets = inbound.drain();
                inboundFrozen = false;
                packets.forEach(PacketUtils::receivePacketNoEvent);
            }
            case OUTBOUND -> {
                List<Packet<?>> packets = outbound.drain();
                outboundFrozen = false;
                packets.forEach(this::send);
            }
            case BOTH -> {
                List<Packet<?>> out = outbound.drain();
                List<Packet<?>> in = inbound.drain();
                inboundFrozen = false;
                outboundFrozen = false;
                out.forEach(this::send);
                in.forEach(PacketUtils::receivePacketNoEvent);
            }
        }
    }


    public void dispatchInboundBatch(int maxPackets) {
        if (maxPackets <= 0) {
            return;
        }

        inbound.drainN(maxPackets, PacketUtils::receivePacketNoEvent);
        if (inbound.size() == 0) {
            inboundFrozen = false;
        }
    }

    public void dispatchAll() {
        dispatch(PacketDirection.BOTH);
    }

    public void poll(PacketDirection direction, boolean flush, int count) {
        int limit = flush ? Integer.MAX_VALUE : count;
        if (limit <= 0) {
            return;
        }

        Consumer<Packet<?>> inHandler = PacketUtils::receivePacketNoEvent;
        switch (direction) {
            case INBOUND -> inbound.drainN(limit, inHandler);
            case OUTBOUND -> outbound.drainN(limit, this::send);
            case BOTH -> {
                inbound.drainN(limit, inHandler);
                outbound.drainN(limit, this::send);
            }
        }
    }

    public boolean isFrozen(PacketDirection direction) {
        return switch (Objects.requireNonNull(direction, "direction")) {
            case INBOUND -> inboundFrozen;
            case OUTBOUND -> outboundFrozen;
            case BOTH -> inboundFrozen && outboundFrozen;
        };
    }

    public int queueSize(PacketDirection direction) {
        return switch (Objects.requireNonNull(direction, "direction")) {
            case INBOUND -> inbound.size();
            case OUTBOUND -> outbound.size();
            case BOTH -> inbound.size() + outbound.size();
        };
    }

    public Packet<?> peek(PacketDirection direction) {
        if (direction == PacketDirection.BOTH) {
            throw new IllegalArgumentException("peek does not support BOTH");
        }
        return queue(direction).peek();
    }

    public Packet<?> take(PacketDirection direction) {
        return queue(direction).take();
    }

    public int peekTick(PacketDirection direction) {
        return queue(direction).peekTick();
    }

    public void cleanup() {
        inboundFrozen = false;
        outboundFrozen = false;
        inbound.clear();
        outbound.clear();
        internalTimer = 0;
    }

    public PacketEvent process(Packet<?> packet, PacketEvent.PacketState state) {
        PacketEvent event = EventBus.call(new PacketEvent(packet, state));
        if (!event.isCancel()) {
            capture(event);
        }
        return event;
    }

    public boolean inboundsHook(Packet<?> packet) {
        try {
            capturedThisCycle = false;
            inboundHookPacket.remove();

            // These packets change the connection protocol/pipeline and are
            // never delayed or exposed to module packet listeners.
            if (PacketUtils.isProtocolControl(packet)) {
                inboundHookPacket.set(packet);
                return true;
            }

            PacketEvent event = new PacketEvent(packet, PacketEvent.PacketState.RECEIVE);
            EventBus.call(event);
            if (capturedThisCycle || event.isCancel()) {
                return false;
            }
            Packet<?> current = event.getPacket();
            if (current == null) return false;
            if (inboundFrozen && !PacketUtils.isEssential(current)) {
                if (!inbound.enqueue(current, internalTimer)) {
                    System.err.println("Packet queue is full, this shouldn't happen!");
                    dispatch(PacketDirection.INBOUND);
                    inboundHookPacket.set(current);
                    return true;
                }
                return false;
            }
            inboundHookPacket.set(current);
            return true;
        } catch (Exception exception) {
            System.err.println("[CentralPacketPipeline] inboundsHook exception on: " + packet.getClass().getSimpleName());
            exception.printStackTrace();
            inboundHookPacket.set(packet);
            return true;
        }
    }

    /** Returns and clears a replacement selected by the most recent inbound hook. */
    public Packet<?> consumeInboundHookPacket() {
        Packet<?> packet = inboundHookPacket.get();
        inboundHookPacket.remove();
        return packet;
    }

    public boolean outboundsHook(Packet<?> packet) {
        try {
            Packet<?> current = packet;
            Packet<?> original = packet;

            int limit = 16;
            for (int i = 0; i < limit; i++) {
                capturedThisCycle = false;
                PacketEvent event = new PacketEvent(current, PacketEvent.PacketState.SEND);
                EventBus.call(event);
                if (capturedThisCycle || event.isCancel()) {
                    return false;
                }

                Packet<?> next = event.getPacket();
                if (next == current) {
                    break;
                }
                current = next;
            }

            if (current != original) {
                if (outboundFrozen && !PacketUtils.isEssential(current)) {
                    if (!outbound.enqueue(current, internalTimer)) {
                        System.err.println("Packet queue is full, this shouldn't happen!");
                        dispatch(PacketDirection.OUTBOUND);
                        send(current);
                        return false;
                    }
                    return false;
                }
                send(current);
                return false;
            }

            if (outboundFrozen && !PacketUtils.isEssential(original)) {
                if (!outbound.enqueue(original, internalTimer)) {
                    System.err.println("Packet queue is full, this shouldn't happen!");
                    dispatch(PacketDirection.OUTBOUND);
                    return true;
                }
                return false;
            }
            return true;
        } catch (Exception exception) {
            System.err.println("[CentralPacketPipeline] outboundsHook exception on: " + packet.getClass().getSimpleName());
            exception.printStackTrace();
            return true;
        }
    }

    public void capture(PacketEvent event) {
        if (capturedThisCycle) {
            throw new IllegalStateException("Already captured");
        }
        if (event.isCancel()) {
            throw new IllegalStateException("Trying to capture a cancelled packet");
        }

        PacketDirection direction = event.getPacketState() == PacketEvent.PacketState.RECEIVE
                ? PacketDirection.INBOUND : PacketDirection.OUTBOUND;

        if (direction == PacketDirection.INBOUND && !inboundFrozen) {
            throw new IllegalStateException("Capture INBOUNDS without freeze");
        }
        if (direction == PacketDirection.OUTBOUND && !outboundFrozen) {
            throw new IllegalStateException("Capture OUTBOUNDS without freeze");
        }

        if (!queue(direction).enqueue(event.getPacket(), internalTimer)) {
            System.err.println("Packet queue is full, this shouldn't happen!");
            dispatch(direction);
            if (direction == PacketDirection.INBOUND) {
                PacketUtils.receivePacketNoEvent(event.getPacket());
            } else {
                send(event.getPacket());
            }
            return;
        }

        capturedThisCycle = true;
        event.setCancel(true);
    }

    @EventTarget(priority = Priority.Lowest)
    private void onTick(EventTick event) {
        if (event.isPre() && minecraft.level != null && minecraft.player != null) internalTimer++;
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        cleanup();
    }

    private ConcurrentPacketQueue queue(PacketDirection direction) {
        return direction == PacketDirection.INBOUND ? inbound : outbound;
    }

    private void send(Packet<?> packet) {
        PacketUtils.sendPacketNoEvent(packet);
    }
}
