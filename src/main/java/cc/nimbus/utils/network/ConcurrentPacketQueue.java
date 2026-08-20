package cc.nimbus.utils.network;

import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConcurrentPacketQueue {
    private final Packet<?>[] packets;
    private final int[] ticks;
    private final int mask;
    private final int capacity;
    private volatile long head;
    private volatile long tail;

    public ConcurrentPacketQueue() {
        this(131072);
    }

    public ConcurrentPacketQueue(int requestedCapacity) {
        this.capacity = Integer.highestOneBit(Math.max(requestedCapacity, 2) - 1) << 1;
        this.packets = new Packet<?>[capacity];
        this.ticks = new int[capacity];
        this.mask = capacity - 1;
    }

    public boolean enqueue(Packet<?> packet, int tick) {
        long currentTail = tail;
        if (currentTail - head >= capacity) {
            return false;
        }
        int index = (int) (currentTail & mask);
        packets[index] = packet;
        ticks[index] = tick;
        tail = currentTail + 1;
        return true;
    }

    public Packet<?> take() {
        long currentHead = head;
        if (currentHead == tail) {
            return null;
        }
        int index = (int) (currentHead & mask);
        Packet<?> packet = packets[index];
        packets[index] = null;
        head = currentHead + 1;
        return packet;
    }

    public Packet<?> peek() {
        return head == tail ? null : packets[(int) (head & mask)];
    }

    public int peekTick() {
        return head == tail ? -1 : ticks[(int) (head & mask)];
    }

    public List<Packet<?>> drain() {
        long currentHead = head;
        long currentTail = tail;
        int size = (int) (currentTail - currentHead);
        List<Packet<?>> list = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            int packetIndex = (int) ((currentHead + index) & mask);
            list.add(packets[packetIndex]);
            packets[packetIndex] = null;
        }
        head = currentTail;
        return list;
    }

    public void drainN(int limit, Consumer<Packet<?>> handler) {
        long currentHead = head;
        long currentTail = tail;
        int size = Math.min(limit, (int) (currentTail - currentHead));
        if (size <= 0) {
            return;
        }

        Packet<?>[] batch = new Packet<?>[size];
        for (int index = 0; index < size; index++) {
            int packetIndex = (int) ((currentHead + index) & mask);
            batch[index] = packets[packetIndex];
            packets[packetIndex] = null;
        }
        head = currentHead + size;
        for (Packet<?> packet : batch) {
            handler.accept(packet);
        }
    }

    public int size() {
        return (int) (tail - head);
    }

    public void clear() {
        long currentHead = head;
        long currentTail = tail;
        for (long index = currentHead; index < currentTail; index++) {
            packets[(int) (index & mask)] = null;
        }
        head = currentTail;
    }
}
