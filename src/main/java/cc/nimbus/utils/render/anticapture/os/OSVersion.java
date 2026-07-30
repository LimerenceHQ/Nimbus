package cc.nimbus.utils.render.anticapture.os;

import cc.nimbus.utils.render.anticapture.jna.NtDll;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

@SuppressWarnings("unused")
public final class OSVersion {
    private static final OS os;
    private static final int majorVersion;
    private static final int buildVersion;

    static {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        int osVersion = -1;
        int osBuild = -1;
        if (osName.contains("win")) {
            os = OS.Windows;
            try {
                NtDll.RTL_OSVERSIONINFOW info = new NtDll.RTL_OSVERSIONINFOW();
                if (NtDll.INSTANCE.RtlGetVersion(info) == 0) {
                    osVersion = info.dwMajorVersion;
                    osBuild = info.dwBuildNumber;
                }
            } catch (RuntimeException | LinkageError exception) {
                System.err.println("[AntiCapture] Unable to query Windows version: " + exception.getMessage());
            }
        } else if (osName.contains("mac")) {
            os = OS.MacOS;
            try {
                Process process = new ProcessBuilder("sw_vers", "-productVersion").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String version = reader.readLine();
                    if (version != null) {
                        osVersion = Integer.parseInt(version.split("\\.")[0]);
                    }
                }
            } catch (Exception exception) {
                System.err.println("[AntiCapture] Unable to query macOS version: " + exception.getMessage());
            }
        } else {
            os = OS.IDK;
        }

        majorVersion = osVersion;
        buildVersion = osBuild;
    }

    private OSVersion() {
    }

    public static OS getOs() {
        return os;
    }

    public static int getVersion() {
        return majorVersion;
    }

    public static int getBuildVersion() {
        return buildVersion;
    }
}
