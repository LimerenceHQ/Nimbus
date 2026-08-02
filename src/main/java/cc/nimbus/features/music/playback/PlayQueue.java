package cc.nimbus.features.music.playback;

import cc.nimbus.features.music.model.Music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlayQueue {
    private final List<Music> source = new ArrayList<>();
    private final List<Music> order = new ArrayList<>();
    private PlayMode mode = PlayMode.LOOP_LIST;
    private int index;

    public synchronized void replace(List<Music> songs, int startIndex) {
        Objects.requireNonNull(songs, "songs");
        source.clear();
        songs.stream().filter(Objects::nonNull).forEach(source::add);
        Music selected = startIndex >= 0 && startIndex < source.size() ? source.get(startIndex) : null;
        rebuild(selected);
    }

    public synchronized Music current() { return order.isEmpty() ? null : order.get(normalize(index)); }
    public synchronized Music next(boolean manual) {
        if (order.isEmpty()) return null;
        if (!manual && mode == PlayMode.SINGLE) return current();
        index = normalize(index + 1);
        return order.get(index);
    }
    public synchronized Music previous() {
        if (order.isEmpty()) return null;
        index = normalize(index - 1);
        return order.get(index);
    }
    public synchronized Music select(int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= order.size()) return null;
        index = selectedIndex;
        return order.get(index);
    }
    public synchronized Music peekNext() {
        if (order.isEmpty()) return null;
        return mode == PlayMode.SINGLE ? current() : order.get(normalize(index + 1));
    }
    public synchronized void mode(PlayMode value) {
        Objects.requireNonNull(value, "mode");
        if (mode == value) return;
        Music selected = current();
        mode = value;
        rebuild(selected);
    }
    public synchronized PlayMode mode() { return mode; }
    public synchronized int index() { return order.isEmpty() ? -1 : normalize(index); }
    public synchronized int size() { return order.size(); }
    public synchronized List<Music> songs() { return List.copyOf(order); }
    public synchronized PlaybackQueueState snapshot() {
        return order.isEmpty()
                ? PlaybackQueueState.empty()
                : new PlaybackQueueState(order, normalize(index));
    }

    private void rebuild(Music selected) {
        order.clear();
        order.addAll(source);
        if (mode == PlayMode.SHUFFLE) Collections.shuffle(order);
        index = selected == null ? 0 : Math.max(0, order.indexOf(selected));
    }
    private int normalize(int value) { return Math.floorMod(value, order.size()); }
}
