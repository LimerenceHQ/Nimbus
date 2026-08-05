package cc.nimbus.features.music.integration.windows;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

final class WinRt {
    static final Guid.GUID IID_ASYNC_INFO = guid("00000036-0000-0000-C000-000000000046");

    private static final int RO_INIT_MULTITHREADED = 1;
    private static final int E_NOINTERFACE = 0x80004002;
    private static final int ASYNC_STARTED = 0;
    private static final int ASYNC_COMPLETED = 1;
    private static final int ASYNC_CANCELED = 2;
    private static final int ASYNC_ERROR = 3;
    private static final long ASYNC_POLL_MILLIS = 10L;
    private static final ComBase COM_BASE = Native.load("combase", ComBase.class, W32APIOptions.DEFAULT_OPTIONS);

    private WinRt() { }

    static void initialize() {
        check(COM_BASE.RoInitialize(RO_INIT_MULTITHREADED), "RoInitialize");
    }

    static void uninitialize() {
        COM_BASE.RoUninitialize();
    }

    static ComObject activationFactory(String runtimeClass, Guid.GUID iid) {
        try (HString name = new HString(runtimeClass)) {
            PointerByReference result = new PointerByReference();
            check(COM_BASE.RoGetActivationFactory(name.pointer(), iid.getPointer(), result),
                    "RoGetActivationFactory(" + runtimeClass + ')');
            return new ComObject(result.getValue());
        }
    }

    static ComObject awaitObject(ComObject operation, Duration timeout) {
        await(operation, timeout);
        return operation.object(8);
    }

    static boolean awaitBoolean(ComObject operation, Duration timeout) {
        await(operation, timeout);
        Memory result = new Memory(1L);
        operation.call(8, result);
        return result.getByte(0L) != 0;
    }

    private static void await(ComObject operation, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (ComObject info = operation.query(IID_ASYNC_INFO)) {
            while (true) {
                IntByReference status = new IntByReference();
                info.call(7, status);
                switch (status.getValue()) {
                    case ASYNC_COMPLETED -> { return; }
                    case ASYNC_CANCELED -> throw new IllegalStateException("Windows Runtime operation was canceled");
                    case ASYNC_ERROR -> {
                        IntByReference error = new IntByReference();
                        info.call(8, error);
                        check(error.getValue(), "Windows Runtime asynchronous operation");
                        throw new IllegalStateException("Windows Runtime operation failed");
                    }
                    case ASYNC_STARTED -> { }
                    default -> throw new IllegalStateException("Unknown Windows Runtime async status: " + status.getValue());
                }
                if (System.nanoTime() >= deadline) {
                    try { info.call(9); }
                    catch (RuntimeException ignored) { }
                    throw new IllegalStateException("Windows Runtime operation timed out");
                }
                try {
                    Thread.sleep(ASYNC_POLL_MILLIS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for Windows Runtime", error);
                }
            }
        }
    }

    static int invoke(Pointer object, int index, Object... arguments) {
        if (object == null || Pointer.nativeValue(object) == 0L) {
            throw new IllegalStateException("Cannot invoke a null COM object");
        }
        Pointer vtable = object.getPointer(0L);
        Pointer address = vtable.getPointer((long) index * Native.POINTER_SIZE);
        Function function = Function.getFunction(address, Function.ALT_CONVENTION);
        Object[] values = new Object[arguments.length + 1];
        values[0] = object;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        return function.invokeInt(values);
    }

    static Guid.GUID guid(String value) {
        Guid.GUID result = new Guid.GUID(value);
        result.write();
        return result;
    }

    static void check(int result, String operation) {
        if (result < 0) {
            throw new IllegalStateException(operation + " failed with HRESULT 0x"
                    + Integer.toUnsignedString(result, 16).toUpperCase());
        }
    }

    static final class ComObject implements AutoCloseable {
        private final Pointer pointer;
        private final AtomicBoolean closed = new AtomicBoolean();

        ComObject(Pointer pointer) {
            if (pointer == null || Pointer.nativeValue(pointer) == 0L) {
                throw new IllegalStateException("COM returned a null object");
            }
            this.pointer = pointer;
        }

        Pointer pointer() {
            if (closed.get()) throw new IllegalStateException("COM object is closed");
            return pointer;
        }

        void call(int index, Object... arguments) {
            check(invoke(pointer(), index, arguments), "COM method " + index);
        }

        ComObject object(int index, Object... arguments) {
            ComObject result = optionalObject(index, arguments);
            if (result == null) throw new IllegalStateException("COM method " + index + " returned null");
            return result;
        }

        ComObject optionalObject(int index, Object... arguments) {
            PointerByReference result = new PointerByReference();
            Object[] values = Arrays.copyOf(arguments, arguments.length + 1);
            values[arguments.length] = result;
            call(index, values);
            Pointer value = result.getValue();
            return value == null || Pointer.nativeValue(value) == 0L ? null : new ComObject(value);
        }

        ComObject query(Guid.GUID iid) {
            PointerByReference result = new PointerByReference();
            int status = invoke(pointer(), 0, iid.getPointer(), result);
            if (status == E_NOINTERFACE) return null;
            check(status, "QueryInterface");
            return new ComObject(result.getValue());
        }

        String string(int index) {
            PointerByReference result = new PointerByReference();
            call(index, result);
            Pointer value = result.getValue();
            if (value == null || Pointer.nativeValue(value) == 0L) return "";
            try {
                IntByReference length = new IntByReference();
                Pointer characters = COM_BASE.WindowsGetStringRawBuffer(value, length);
                return characters == null ? "" : characters.getWideString(0L);
            } finally {
                COM_BASE.WindowsDeleteString(value);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) invoke(pointer, 2);
        }
    }

    private static final class HString implements AutoCloseable {
        private final Pointer pointer;

        private HString(String value) {
            String normalized = value == null ? "" : value;
            PointerByReference result = new PointerByReference();
            check(COM_BASE.WindowsCreateString(new WString(normalized), normalized.length(), result),
                    "WindowsCreateString");
            pointer = result.getValue();
        }

        private Pointer pointer() {
            return pointer;
        }

        @Override
        public void close() {
            if (pointer != null && Pointer.nativeValue(pointer) != 0L) COM_BASE.WindowsDeleteString(pointer);
        }
    }

    private interface ComBase extends StdCallLibrary {
        int RoInitialize(int type);
        void RoUninitialize();
        int RoGetActivationFactory(Pointer classId, Pointer iid, PointerByReference factory);
        int WindowsCreateString(WString source, int length, PointerByReference value);
        Pointer WindowsGetStringRawBuffer(Pointer value, IntByReference length);
        int WindowsDeleteString(Pointer value);
    }
}
