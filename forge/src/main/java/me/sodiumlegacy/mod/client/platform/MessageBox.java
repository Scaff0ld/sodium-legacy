package me.sodiumlegacy.mod.client.platform;

import me.sodiumlegacy.mod.client.platform.windows.api.msgbox.MsgBoxParamSw;
import me.sodiumlegacy.mod.client.platform.windows.api.msgbox.MsgBoxCallback;
import me.sodiumlegacy.mod.client.platform.windows.api.msgbox.MsgBoxCallbackI;
import me.sodiumlegacy.mod.client.platform.windows.api.User32;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import me.sodiumlegacy.lwjgl3shim.system.MemoryStack;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.awt.Desktop;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;

public class MessageBox {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-MessageBox");

    private static final @Nullable MessageBoxImpl IMPL = MessageBoxImpl.chooseImpl();

    public static void showMessageBox(@Nullable Minecraft mc,
                                      IconType icon, String title,
                                      String description,
                                      @Nullable String helpUrl)
    {
        if (IMPL != null) {
            IMPL.showMessageBox(mc, icon, title, description, helpUrl);
        }
    }

    private interface MessageBoxImpl {
        static @Nullable MessageBoxImpl chooseImpl() {
            if (Util.getOSType() == Util.EnumOS.WINDOWS) {
                return new WindowsMessageBoxImpl();
            }

            // TODO: Provide an implementation on other platforms
            return null;
        }

        void showMessageBox(@Nullable Minecraft mc,
                            IconType icon, String title,
                            String description,
                            @Nullable String helpUrl);
    }

    private static class WindowsMessageBoxImpl implements MessageBoxImpl {
        @Override
        public void showMessageBox(@Nullable Minecraft mc,
                                   IconType icon, String title,
                                   String description,
                                   @Nullable String helpUrl) {
            Objects.requireNonNull(title);
            Objects.requireNonNull(description);
            Objects.requireNonNull(icon);

            final MsgBoxCallback msgBoxCallback;

            if (helpUrl != null) {
                msgBoxCallback = MsgBoxCallback.create(new MsgBoxCallbackI() {
                    @Override
                    public void invoke(long lpHelpInfo) {
                        openUri(helpUrl);
                    }

                    // A raw delegate is never its own trampoline; MsgBoxCallback.create wraps it
                    // into a Callback container which supplies the address.
                    @Override
                    public long address() {
                        return MemoryUtil.NULL;
                    }
                });
            } else {
                msgBoxCallback = null;
            }

            final long hWndOwner;

            // On MC 1.8.9 the Window type does not carry the operating system window handle, so
            // the message box cannot be given an owner window; it is simply a top-level window.
            if (mc != null) {
                LOGGER.warn("Cannot attach the message box to the given window; using a top-level message box");
            }

            hWndOwner = MemoryUtil.NULL;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer lpText = stack.malloc(MemoryUtil.memLengthUTF16(description, true));
                MemoryUtil.memUTF16(description, true, lpText);

                ByteBuffer lpCaption = stack.malloc(MemoryUtil.memLengthUTF16(title, true));
                MemoryUtil.memUTF16(title, true, lpCaption);

                MsgBoxParamSw params = MsgBoxParamSw.allocate(stack);
                params.setCbSize(MsgBoxParamSw.SIZEOF);
                params.setHWndOwner(hWndOwner);
                params.setText(lpText);
                params.setCaption(lpCaption);
                params.setStyle(getStyle(icon, msgBoxCallback != null));
                params.setCallback(msgBoxCallback);

                User32.callMessageBoxIndirectW(params);
            } finally {
                if (msgBoxCallback != null) {
                    msgBoxCallback.free();
                }
            }
        }

        private static int getStyle(IconType icon, boolean showHelp) {
            int style;

            switch (icon) {
                case INFO: style = 0x00000040; break; /* MB_ICONINFORMATION */
                case WARNING: style = 0x00000030; break; /* MB_ICONWARNING */
                case ERROR: style = 0x00000010; break; /* MB_ICONERROR */
                default: throw new AssertionError(icon);
            }

            if (showHelp) {
                style |= 0x00004000 /* MB_HELP */;
            }

            return style;
        }

        /**
         * Opens an URL in the user's browser. Minecraft 1.8.9 does not provide the
         * {@code open(String)} helper on its {@link Util.EnumOS} type, so the Java
         * desktop integration is used directly.
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

    }

    public enum IconType {
        INFO,
        WARNING,
        ERROR
    }
}

