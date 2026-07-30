package cc.nimbus.utils.render.anticapture;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import cc.nimbus.utils.render.anticapture.jna.User32Ext;
import cc.nimbus.utils.render.anticapture.os.OSVersion;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("unused")
public final class AntiCapture {
    private static final Queue<Long> applying = new ConcurrentLinkedQueue<>();

    private AntiCapture() {
    }

    public static class Windows {
        private Windows() {
        }

        public static void applyOwner(long windowHandle, long ownerHandle) {
            if (windowHandle == 0 || ownerHandle == 0) {
                return;
            }

            WinDef.HWND overlayHwnd = hwnd(windowHandle);
            WinDef.HWND ownerHwnd = hwnd(ownerHandle);
            User32Ext.INSTANCE.SetWindowLongPtrA(overlayHwnd, User32Ext.GWLP_HWNDPARENT, ownerHwnd.getPointer());
            User32Ext.INSTANCE.SetWindowPos(overlayHwnd, ownerHwnd, 0, 0, 0, 0,
                    User32Ext.SWP_NOMOVE | User32Ext.SWP_NOSIZE | User32Ext.SWP_NOACTIVATE | User32Ext.SWP_FRAMECHANGED);
        }

        public static void applyStyle(long windowHandle) {
            if (windowHandle == 0) {
                return;
            }

            WinDef.HWND overlayHwnd = hwnd(windowHandle);
            int oldStyle = User32Ext.INSTANCE.GetWindowLongA(overlayHwnd, User32Ext.GWL_EXSTYLE);
            int newStyle = oldStyle | User32Ext.WS_EX_LAYERED | User32Ext.WS_EX_TRANSPARENT | User32Ext.WS_EX_NOACTIVATE;
            User32Ext.INSTANCE.SetWindowLongA(overlayHwnd, User32Ext.GWL_EXSTYLE, newStyle);
        }

        public static void applyAffinity(long windowHandle) {
            if (windowHandle != 0) {
                User32Ext.INSTANCE.SetWindowDisplayAffinity(hwnd(windowHandle), captureAffinity());
            }
        }

        public static void resetOwner(long windowHandle) {
            if (windowHandle == 0) {
                return;
            }

            WinDef.HWND overlayHwnd = hwnd(windowHandle);
            User32Ext.INSTANCE.SetWindowLongPtrA(overlayHwnd, User32Ext.GWLP_HWNDPARENT, Pointer.NULL);
            User32Ext.INSTANCE.SetWindowPos(overlayHwnd, null, 0, 0, 0, 0,
                    User32Ext.SWP_NOMOVE | User32Ext.SWP_NOSIZE | User32Ext.SWP_NOACTIVATE
                            | User32Ext.SWP_FRAMECHANGED | User32Ext.SWP_NOZORDER);
        }

        public static void resetStyle(long windowHandle) {
            if (windowHandle == 0) {
                return;
            }

            WinDef.HWND overlayHwnd = hwnd(windowHandle);
            int oldStyle = User32Ext.INSTANCE.GetWindowLongA(overlayHwnd, User32Ext.GWL_EXSTYLE);
            int flags = User32Ext.WS_EX_LAYERED | User32Ext.WS_EX_TRANSPARENT | User32Ext.WS_EX_NOACTIVATE;
            User32Ext.INSTANCE.SetWindowLongA(overlayHwnd, User32Ext.GWL_EXSTYLE, oldStyle & ~flags);
        }

        public static void resetAffinity(long windowHandle) {
            if (windowHandle != 0) {
                User32Ext.INSTANCE.SetWindowDisplayAffinity(hwnd(windowHandle), User32Ext.WDA_NONE);
            }
        }
    }

    public static void applyWindows(long overlay) {
        if (overlay == 0 || applying.contains(overlay)) {
            return;
        }

        try {
            int affinity = captureAffinity();
            if (affinity != User32Ext.WDA_NONE) {
                User32Ext.INSTANCE.SetWindowDisplayAffinity(hwnd(overlay), affinity);
                applying.add(overlay);
            } else {
                System.err.println("[AntiCapture] Unsupported OS: " + OSVersion.getOs() + " "
                        + OSVersion.getVersion() + " " + OSVersion.getBuildVersion());
            }
        } catch (RuntimeException | LinkageError exception) {
            System.err.println("[AntiCapture] Unable to apply capture affinity: " + exception.getMessage());
        }
    }

    public static void resetWindows(long overlay) {
        if (overlay == 0 || !applying.contains(overlay)) {
            return;
        }

        try {
            User32Ext.INSTANCE.SetWindowDisplayAffinity(hwnd(overlay), User32Ext.WDA_NONE);
        } catch (RuntimeException | LinkageError exception) {
            System.err.println("[AntiCapture] Unable to reset capture affinity: " + exception.getMessage());
        } finally {
            applying.remove(overlay);
        }
    }

    private static WinDef.HWND hwnd(long handle) {
        return new WinDef.HWND(new Pointer(handle));
    }

    private static int captureAffinity() {
        if (OSVersion.getVersion() > 10
                || OSVersion.getVersion() == 10 && OSVersion.getBuildVersion() >= 18362) {
            return User32Ext.WDA_EXCLUDEFROMCAPTURE;
        }
        if (OSVersion.getVersion() >= 6) {
            return User32Ext.WDA_MONITOR;
        }
        return User32Ext.WDA_NONE;
    }
}
