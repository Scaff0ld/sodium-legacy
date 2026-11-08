package me.sodiumlegacy.mod.client.platform.windows.api;

import me.sodiumlegacy.mod.client.platform.windows.api.msgbox.MsgBoxParamSw;

/**
 * User32 bindings (LWJGL 3 style).
 *
 * <p>Function pointers cannot be resolved or invoked on the MC 1.8.9 / LWJGL 2 runtime; the
 * message box call is stubbed and {@link #callMessageBoxIndirectW(MsgBoxParamSw)} throws
 * {@link UnsupportedOperationException}. The public signature is kept intact so that the
 * message box plumbing keeps compiling.</p>
 */
public class User32 {

    private static final String UNSUPPORTED =
            "Windows driver metadata diagnostics are not supported on this runtime yet (TODO 1.8.9)";

    /**
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-messageboxw>Winuser.h Documentation</a>
     */
    public static void callMessageBoxIndirectW(MsgBoxParamSw params) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
