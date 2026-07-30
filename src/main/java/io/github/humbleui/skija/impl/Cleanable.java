package io.github.humbleui.skija.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

public final class Cleanable {
    private static final MethodHandle CREATE_HANDLE;
    private static final MethodHandle CLEAN_HANDLE;

    static {
        MethodHandle createHandle;
        MethodHandle cleanHandle;

        try {
            Class<?> cleanerClass = Class.forName("java.lang.ref.Cleaner");
            Class<?> cleanableClass = Class.forName("java.lang.ref.Cleaner$Cleanable");

            Object cleanerInstance = MethodHandles.publicLookup()
                    .findStatic(cleanerClass, "create", MethodType.methodType(cleanerClass))
                    .invoke();

            createHandle = MethodHandles.publicLookup()
                    .findVirtual(cleanerClass, "register",
                            MethodType.methodType(cleanableClass, Object.class, Runnable.class))
                    .bindTo(cleanerInstance);

            cleanHandle = MethodHandles.publicLookup()
                    .findVirtual(cleanableClass, "clean", MethodType.methodType(void.class));
        } catch (Throwable e) {
            try {
                Class<?> cleanerClass = Class.forName("sun.misc.Cleaner");

                createHandle = MethodHandles.publicLookup()
                        .findStatic(cleanerClass, "create",
                                MethodType.methodType(cleanerClass, Object.class, Runnable.class));

                cleanHandle = MethodHandles.publicLookup()
                        .findVirtual(cleanerClass, "clean", MethodType.methodType(void.class));
            } catch (Throwable ex) {
                throw new NoClassDefFoundError("No cleaner implementation available");
            }
        }

        CREATE_HANDLE = createHandle;
        CLEAN_HANDLE = cleanHandle;
    }

    public static Cleanable register(Object obj, Runnable action) {
        Objects.requireNonNull(obj, "Object cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");

        try {
            Object cleaner = CREATE_HANDLE.invoke(obj, action);
            return new Cleanable(cleaner);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to register cleanable", e);
        }
    }

    private final Object cleaner;

    private Cleanable(Object cleaner) {
        this.cleaner = cleaner;
    }

    public void clean() {
        try {
            CLEAN_HANDLE.invoke(cleaner);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to clean", e);
        }
    }
}