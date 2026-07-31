package cc.nimbus.utils.render.skija.resource;

import io.github.humbleui.skija.impl.Managed;

import java.util.ArrayDeque;
import java.util.Deque;

/** Owns native objects that are valid only for the current submitted frame. */
public final class FrameResources {
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();

    public <T extends AutoCloseable> T own(T resource) {
        if (resource != null) resources.add(resource);
        return resource;
    }

    public void clear() {
        AutoCloseable resource;
        while ((resource = resources.pollLast()) != null) {
            try {
                if (!(resource instanceof Managed managed) || !managed.isClosed()) {
                    resource.close();
                }
            } catch (Exception error) {
                System.err.println("Failed to close frame resource " + resource.getClass().getSimpleName()
                        + ": " + error.getMessage());
            }
        }
    }
}
