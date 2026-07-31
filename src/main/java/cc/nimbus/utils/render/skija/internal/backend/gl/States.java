package cc.nimbus.utils.render.skija.internal.backend.gl;

import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.Deque;

public final class States {
    private static final ThreadLocal<Deque<State>> STATES = ThreadLocal.withInitial(ArrayDeque::new);

    private States() {
    }

    public static void push() {
        int major = GL30.glGetInteger(GL30.GL_MAJOR_VERSION);
        int minor = GL30.glGetInteger(GL30.GL_MINOR_VERSION);
        STATES.get().push(new State(major * 100 + minor * 10).push());
    }

    public static void pop() {
        Deque<State> states = STATES.get();
        if (states.isEmpty()) {
            throw new IllegalStateException("No state to restore.");
        }
        states.pop().pop();
        if (states.isEmpty()) {
            STATES.remove();
        }
    }
}
