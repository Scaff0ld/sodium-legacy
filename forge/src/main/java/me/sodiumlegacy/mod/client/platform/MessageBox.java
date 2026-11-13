package me.sodiumlegacy.mod.client.platform;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.awt.Desktop;
import java.net.URI;

/**
 * Shows a blocking modal dialog with a compatibility message. The native Win32 message box is
 * not available on the MC 1.8.9 / LWJGL 2 runtime (the upstream implementation drives it through
 * LWJGL 3's libffi), so a Swing dialog is used on every platform instead.
 */
public class MessageBox {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-MessageBox");

    public static void showMessageBox(@Nullable Minecraft mc,
                                      IconType icon, String title,
                                      String description,
                                      @Nullable String helpUrl)
    {
        showFallbackDialog(icon, title, description, helpUrl);
    }

    /**
     * Pure-Java modal dialog. Swing/AWT is available on the 1.8.9 client runtime on every
     * platform.
     */
    private static void showFallbackDialog(IconType icon, String title,
                                           String description,
                                           @Nullable String helpUrl) {
        int messageType;

        switch (icon) {
            case INFO: messageType = javax.swing.JOptionPane.INFORMATION_MESSAGE; break;
            case WARNING: messageType = javax.swing.JOptionPane.WARNING_MESSAGE; break;
            case ERROR: messageType = javax.swing.JOptionPane.ERROR_MESSAGE; break;
            default: throw new AssertionError(icon);
        }

        if (helpUrl == null) {
            javax.swing.JOptionPane.showMessageDialog(null, description, title, messageType);
            return;
        }

        Object[] options = { "OK", "Help" };
        int result = javax.swing.JOptionPane.showOptionDialog(
                null,
                description + "\n\nPress 'Help' to open the documentation.",
                title,
                javax.swing.JOptionPane.DEFAULT_OPTION,
                messageType,
                null,
                options,
                options[0]);

        if (result == 1) {
            openUri(helpUrl);
        }
    }

    /**
     * Opens an URL in the user's browser. Minecraft 1.8.9 does not provide an URL-opening
     * helper on its operating system utility type, so the Java desktop integration is used
     * directly.
     */
    private static void openUri(String uri) {
        try {
            Desktop desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(new URI(uri));
            } else {
                LOGGER.warn("Opening a browser is not supported on this platform; cannot open: {}", uri);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to open the help URI: {}", uri, t);
        }
    }

    public enum IconType {
        INFO,
        WARNING,
        ERROR
    }
}
