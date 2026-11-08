package me.sodiumlegacy.lwjgl3shim.system;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** LWJGL 3-compatibility annotation marking the native type of a member/parameter. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
public @interface NativeType {

    /** The native type name. */
    String value();
}


