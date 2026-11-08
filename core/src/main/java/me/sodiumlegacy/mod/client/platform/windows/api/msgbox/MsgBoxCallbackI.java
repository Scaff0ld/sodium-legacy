package me.sodiumlegacy.mod.client.platform.windows.api.msgbox;

import org.jetbrains.annotations.NotNull;
import me.sodiumlegacy.lwjgl3shim.system.CallbackI;
import me.sodiumlegacy.lwjgl3shim.system.NativeType;
import me.sodiumlegacy.lwjgl3shim.system.libffi.FFICIF;

import static me.sodiumlegacy.lwjgl3shim.system.APIUtil.apiCreateCIF;
import static me.sodiumlegacy.lwjgl3shim.system.MemoryUtil.memGetAddress;
import static me.sodiumlegacy.lwjgl3shim.system.libffi.LibFFI.*;

@NativeType("MSGBOXCALLBACK")
public interface MsgBoxCallbackI extends CallbackI {
    FFICIF CIF = apiCreateCIF(
            FFI_DEFAULT_ABI,
            ffi_type_void,
            ffi_type_pointer
    );

    @Override
    default @NotNull FFICIF getCallInterface() {
        return CIF;
    }

    @Override
    default void callback(long ret, long args) {
        this.invoke(
                memGetAddress(memGetAddress(args)) /* lpHelpInfo */
        );
    }

    void invoke(@NativeType("LPHELPINFO *") long lpHelpInfo);
}

