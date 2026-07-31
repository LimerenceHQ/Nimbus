package cc.nimbus.utils.render.skija.internal.resource;

import io.github.humbleui.skija.impl.Managed;

import java.util.ArrayDeque;
import java.util.Deque;

public final class FrameResources implements AutoCloseable {
    private final Deque<Managed> resources = new ArrayDeque<>();

    public <T extends Managed> T own(T resource) {
        if (resource != null) resources.push(resource);
        return resource;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        while (!resources.isEmpty()) {
            Managed resource = resources.pop();
            try {
                if (!resource.isClosed()) resource.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
