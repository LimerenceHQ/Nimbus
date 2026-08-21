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
import java.util.function.Predicate;

public final class PacketManager implements InstanceManager {
    public static final int DEFAULT_CAPACITY = 131_072;

    private final Minecraft minecraft;
    private final ConcurrentPacketQueue inbound;
    private final ConcurrentPacketQueue outbound;
    private volatile boolean inboundFrozen;
    private volatile boolean outboundFrozen;
    private volatile Predicate<Packet<?>> inboundFreezeFilter;
    private volatile Predicate<Packet<?>> outboundFreezeFilter;
    private int internalTimer;
    private boolean capturedThisCycle;

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
        freeze(direction, packet -> true);
    }

    /** Freezes only the supplied packet classes (and their subclasses). */
    public void freeze(PacketDirection direction, Class<?>... packetTypes) {
        freezePackets(direction, packetTypes);
    }

    /**
     * Freezes only packets accepted by {@code filter}. Essential packets are
     * still allowed through, as with the original direction-wide freeze.
     */
    public void freeze(PacketDirection direction, Predicate<Packet<?>> filter) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(filter, "filter");
        switch (direction) {
            case INBOUND -> {
                inboundFreezeFilter = filter;
                inboundFrozen = true;
            }
            case OUTBOUND -> {
                outboundFreezeFilter = filter;
                outboundFrozen = true;
            }
            case BOTH -> {
                inboundFreezeFilter = filter;
                outboundFreezeFilter = filter;
                inboundFrozen = true;
                outboundFrozen = true;
            }
        }
    }

    /**
     * Convenience overload for callers that want to freeze selected packet
     * types without writing a predicate.
     */
    public void freezePackets(PacketDirection direction, Class<?>... packetTypes) {
        Objects.requireNonNull(packetTypes, "packetTypes");
        Class<?>[] types = packetTypes.clone();
        for (Class<?> type : types) {
            Objects.requireNonNull(type, "packetTypes contains null");
        }
        freeze(direction, packet -> {
            for (Class<?> type : types) {
                if (type.isInstance(packet)) return true;
            }
            return false;
        });
    }


    public void dispatch(PacketDirection direction) {
        switch (direction) {
            case INBOUND -> {
                List<Packet<?>> packets = inbound.drain();
                inboundFrozen = false;
                inboundFreezeFilter = null;
                packets.forEach(PacketUtils::receivePacketNoEvent);
            }
            case OUTBOUND -> {
                List<Packet<?>> packets = outbound.drain();
                outboundFrozen = false;
                outboundFreezeFilter = null;
                packets.forEach(this::send);
            }
            case BOTH -> {
                List<Packet<?>> out = outbound.drain();
                List<Packet<?>> in = inbound.drain();
                inboundFrozen = false;
                outboundFrozen = false;
                inboundFreezeFilter = null;
                outboundFreezeFilter = null;
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
            inboundFreezeFilter = null;
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
        inboundFreezeFilter = null;
        outboundFreezeFilter = null;
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
                if (shouldFreeze(PacketDirection.OUTBOUND, current)) {
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

            if (shouldFreeze(PacketDirection.OUTBOUND, original)) {
                if (!outbound.enqueue(original, internalTimer)) {
                    System.err.println("Packet queue is full, this shouldn't happen!");
                    dispatch(PacketDirection.OUTBOUND);
                    return true;
                }
                return false;
            }
            return true;
        }catch (Exception exception) {
            System.err.println("[CentralPacketPipeline] outboundsHook exception on: " + packet.getClass().getSimpleName());
            exception.printStackTrace();
            return true;
        }
    }


    public boolean inboundsHook(Packet<?> packet) {
        try {
            capturedThisCycle = false;
            PacketEvent event = new PacketEvent(packet, PacketEvent.PacketState.RECEIVE);
            EventBus.call(event);
            if (capturedThisCycle || event.isCancel()) {
                return false;
            }
            if (shouldFreeze(PacketDirection.INBOUND, packet)) {
                if (!inbound.enqueue(packet, internalTimer)) {
                    System.err.println("Packet queue is full, this shouldn't happen!");
                    dispatch(PacketDirection.INBOUND);
                    return true;
                }
                return false;
            }
            return true;
        } catch (Exception exception) {
            System.err.println("[CentralPacketPipeline] inboundsHook exception on: " + packet.getClass().getSimpleName());
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

    private boolean shouldFreeze(PacketDirection direction, Packet<?> packet) {
        if (PacketUtils.isEssential(packet)) return false;
        Predicate<Packet<?>> filter = direction == PacketDirection.INBOUND
                ? inboundFreezeFilter
                : outboundFreezeFilter;
        return filter != null && filter.test(packet);
    }

    private void send(Packet<?> packet) {
        PacketUtils.sendPacketNoEvent(packet);
    }
}
