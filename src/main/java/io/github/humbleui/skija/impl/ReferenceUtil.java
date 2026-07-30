package io.github.humbleui.skija.impl;

import java.lang.reflect.Method;

public class ReferenceUtil {
    private static final Method method_;

    static {
        Method method;
        try {
            method = java.lang.ref.Reference.class.getMethod("reachabilityFence", Object.class);
        } catch (NoSuchMethodException ignored) {
            method = null;
        }
        method_ = method;
    }

    public static void reachabilityFence(Object ref) {
        if (method_ != null) {
            try {
                method_.invoke(null, ref);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to invoke Reference.reachabilityFence", e);
            }
        }
    }
}
