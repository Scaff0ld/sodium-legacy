package me.sodiumlegacy.mod.client.platform.windows.api.msgbox;

import me.sodiumlegacy.lwjgl3shim.system.Callback;
import me.sodiumlegacy.lwjgl3shim.system.MemoryUtil;

public abstract class MsgBoxCallback extends Callback implements MsgBoxCallbackI {
    public static MsgBoxCallback create(MsgBoxCallbackI instance) {
        if (instance instanceof MsgBoxCallback) {
            MsgBoxCallback callback = (MsgBoxCallback) instance;

            return callback;
        }

        // Plain delegates carry no native trampoline of their own; report a NULL function
        // pointer (the 1.8.9 shim runtime has no libffi trampolines anyway) and dispatch
        // through Container.invoke.
        return new Container(MemoryUtil.NULL, instance);
    }

    private MsgBoxCallback(long functionPointer) {
        super(functionPointer);
    }

    private static final class Container extends MsgBoxCallback {

        private final MsgBoxCallbackI delegate;

        Container(long functionPointer, MsgBoxCallbackI delegate) {
            super(functionPointer);

            this.delegate = delegate;
        }

        @Override
        public void invoke(long lpHelpInfo) {
            this.delegate.invoke(lpHelpInfo);
        }
    }
}

