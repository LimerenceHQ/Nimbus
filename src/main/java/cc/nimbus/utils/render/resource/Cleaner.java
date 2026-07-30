package cc.nimbus.utils.render.resource;

import io.github.humbleui.skija.impl.Managed;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@SuppressWarnings("unused")
public class Cleaner {
    private final Deque<Object> resource = new ConcurrentLinkedDeque<>();
    private final Deque<Runnable> persistent_task = new ConcurrentLinkedDeque<>();

    public <T> T addResource(T resource) {
        if (resource != null) {
            this.resource.add(resource);
        }
        return resource;
    }


    public void addPersistentTask(Runnable task) {
        if (task != null) {
            persistent_task.add(task);
        }
    }

    public void clear() {
        if (!persistent_task.isEmpty()) {
            handleTask(persistent_task);
        }

        if (!resource.isEmpty()) {
            handleResource(resource);
            resource.clear();
        }
    }

    @SuppressWarnings("all")
    private void handleResource(Collection<Object> cleaners) {
        for (Object type : cleaners) {
            if (type == null) continue;

            String classSimpleName = type.getClass().getSimpleName();

            try {
                if (type instanceof Managed) {
                    Managed managed = (Managed) type;
                    if (!managed.isClosed()) {
                        managed.close();
                    }
                } else if (type instanceof AutoCloseable) {
                    AutoCloseable closeable = (AutoCloseable) type;
                    closeable.close();
                }
            } catch (Exception e) {
                System.err.println("Exception during cleaning resource of type: " + classSimpleName);
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("all")
    private void handleTask(Collection<Runnable> cleaners) {
        try {
            cleaners.forEach(Runnable::run);
        } catch (Exception e) {
            System.err.println("Exception during cleaning task: " + e.getMessage());
            e.printStackTrace();
        }
    }
}