package cc.nimbus.managers;

import cc.nimbus.ui.alt.model.MicrosoftAlt;
import cc.nimbus.ui.alt.model.OfflineAlt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsAccounts() {
        Path directory = temporaryDirectory.resolve("accounts");
        AccountManager first = new AccountManager(directory);
        first.init();
        try {
            assertTrue(first.addAccount(new OfflineAlt("OfflineUser")));
            MicrosoftAlt microsoft = new MicrosoftAlt("MicrosoftUser", "refresh-token");
            assertTrue(first.addAccount(microsoft));
            assertTrue(first.setLastAccount(microsoft));
        } finally {
            first.shutdown();
        }

        AccountManager restored = new AccountManager(directory);
        restored.init();
        try {
            assertEquals(2, restored.getAccounts().size());
            assertInstanceOf(MicrosoftAlt.class, restored.getLastAccount());
            assertEquals("MicrosoftUser", restored.getLastAccount().getUserName());
        } finally {
            restored.shutdown();
        }
    }

    @Test
    void doesNotMigrateLegacyAccountFiles() throws Exception {
        Path legacyDirectory = temporaryDirectory.resolve("configs");
        Path accountDirectory = temporaryDirectory.resolve("accounts");
        Files.createDirectories(legacyDirectory);
        Path legacyAccounts = legacyDirectory.resolve("alts.json");
        Path legacyLastAccount = legacyDirectory.resolve("lastalt.json");
        Files.writeString(legacyAccounts, "[]");
        Files.writeString(legacyLastAccount, "{}");

        AccountManager manager = new AccountManager(accountDirectory);
        manager.init();
        try {
            assertTrue(manager.getAccounts().isEmpty());
            assertTrue(Files.exists(legacyAccounts));
            assertTrue(Files.exists(legacyLastAccount));
            assertFalse(Files.exists(accountDirectory.resolve("accounts.json")));
            assertFalse(Files.exists(accountDirectory.resolve("last.json")));
        } finally {
            manager.shutdown();
        }
    }
}
