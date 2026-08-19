package cc.nimbus.managers;

import cc.nimbus.core.interfaces.InstanceManager;
import cc.nimbus.event.EventBus;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.component.Priority;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.event.impl.game.EventWorldLoad;
import cc.nimbus.event.impl.network.EventPacketReceive;
import cc.nimbus.event.impl.network.EventPacketSend;
import cc.nimbus.utils.network.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PacketManager implements InstanceManager {
    public static final int DEFAULT_CAPACITY = 131_072;

    private final Minecraft minecraft;
    private final PacketQueue inbound;
    private final PacketQueue outbound;
    private volatile boolean inboundFrozen;
    private volatile boolean outboundFrozen;
    private volatile int internalTimer;

    public PacketManager(Minecraft minecraft) {
        this(minecraft, DEFAULT_CAPACITY);
    }

    public PacketManager(Minecraft minecraft, int capacity) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        if (capacity < 2) throw new IllegalArgumentException("capacity must be at least 2");
        this.inbound = new PacketQueue(capacity);
        this.outbound = new PacketQueue(capacity);
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
        switch (Objects.requireNonNull(direction, "direction")) {
            case INBOUND -> inboundFrozen = true;
            case OUTBOUND -> outboundFrozen = true;
            case BOTH -> {
                inboundFrozen = true;
                outboundFrozen = true;
            }
        }
    }

    public void dispatch(PacketDirection direction) {
        switch (Objects.requireNonNull(direction, "direction")) {
            case INBOUND -> {
                inboundFrozen = false;
                replay(inbound, PacketUtils::receivePacketNoEvent);
            }
            case OUTBOUND -> {
                outboundFrozen = false;
                replay(outbound, PacketUtils::sendPacketNoEvent);
            }
            case BOTH -> {
                outboundFrozen = false;
                inboundFrozen = false;
                replay(outbound, PacketUtils::sendPacketNoEvent);
                replay(inbound, PacketUtils::receivePacketNoEvent);
            }
        }
    }

    public void dispatchAll() {
        dispatch(PacketDirection.BOTH);
    }

    public void poll(PacketDirection direction, boolean flush, int count) {
        int limit = flush ? Integer.MAX_VALUE : count;
        if (limit <= 0) return;

        switch (Objects.requireNonNull(direction, "direction")) {
            case INBOUND -> inbound.drain(Math.min(limit, inbound.size()), PacketUtils::receivePacketNoEvent);
            case OUTBOUND -> outbound.drain(Math.min(limit, outbound.size()), PacketUtils::sendPacketNoEvent);
            case BOTH -> {
                inbound.drain(Math.min(limit, inbound.size()), PacketUtils::receivePacketNoEvent);
                outbound.drain(Math.min(limit, outbound.size()), PacketUtils::sendPacketNoEvent);
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

    @EventTarget(priority = Priority.Lowest)
    private void onPacketReceive(EventPacketReceive event) {
        if (!inboundFrozen || event.getPacket() == null || PacketUtils.isEssential(event.getPacket())) return;
        if (enqueueOrFlush(inbound, event.getPacket(), PacketUtils::receivePacketNoEvent)) {
            event.cancelEvent();
        }
    }

    @EventTarget(priority = Priority.Lowest)
    private void onPacketSend(EventPacketSend event) {
        if (!outboundFrozen || event.getPacket() == null || PacketUtils.isEssential(event.getPacket())) return;
        if (enqueueOrFlush(outbound, event.getPacket(), PacketUtils::sendPacketNoEvent)) {
            event.cancelEvent();
        }
    }

    @EventTarget(priority = Priority.Lowest)
    private void onTick(EventTick event) {
        if (event.isPre() && minecraft.level != null && minecraft.player != null) internalTimer++;
    }

    @EventTarget
    private void onWorldLoad(EventWorldLoad event) {
        cleanup();
    }

    private boolean enqueueOrFlush(PacketQueue queue, Packet<?> packet, Consumer<Packet<?>> fallback) {
        if (packet == null) return false;
        if (queue.offer(packet, internalTimer)) return true;
        queue.drain(Integer.MAX_VALUE, fallback);
        fallback.accept(packet);
        return false;
    }

    private void replay(PacketQueue queue, Consumer<Packet<?>> handler) {
        queue.drain(Integer.MAX_VALUE, handler);
    }

    private PacketQueue queue(PacketDirection direction) {
        return switch (Objects.requireNonNull(direction, "direction")) {
            case INBOUND -> inbound;
            case OUTBOUND -> outbound;
            case BOTH -> throw new IllegalArgumentException("BOTH is not valid for a single queue");
        };
    }

    private static final class PacketQueue {
        private final int capacity;
        private final ArrayDeque<Entry> packets;

        private PacketQueue(int capacity) {
            this.capacity = capacity;
            this.packets = new ArrayDeque<>(capacity);
        }

        private synchronized boolean offer(Packet<?> packet, int tick) {
            if (packets.size() >= capacity) return false;
            packets.addLast(new Entry(packet, tick));
            return true;
        }

        private synchronized Packet<?> take() {
            Entry entry = packets.pollFirst();
            return entry == null ? null : entry.packet();
        }

        private synchronized Packet<?> peek() {
            Entry entry = packets.peekFirst();
            return entry == null ? null : entry.packet();
        }

        private synchronized int peekTick() {
            Entry entry = packets.peekFirst();
            return entry == null ? -1 : entry.tick();
        }

        private synchronized int size() {
            return packets.size();
        }

        private void drain(int limit, Consumer<Packet<?>> handler) {
            List<Packet<?>> drained = new ArrayList<>();
            synchronized (this) {
                int amount = Math.min(Math.max(limit, 0), packets.size());
                for (int index = 0; index < amount; index++) drained.add(packets.removeFirst().packet());
            }
            drained.forEach(handler);
        }

        private synchronized void clear() {
            packets.clear();
        }

        private record Entry(Packet<?> packet, int tick) {
        }
    }
}
