package me.sodiumlegacy.mod.client.compatibility.workarounds.nvidia;

import me.sodiumlegacy.mod.client.compatibility.environment.GLContextInfo;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NvidiaDriverVersion {
    private static final Pattern PATTERN = Pattern.compile("^.*NVIDIA (?<major>\\d+)\\.(?<minor>\\d+)(?<suffix>\\.\\d+)?$");

    private final int major;
    private final int minor;

    public NvidiaDriverVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    public int major() {
        return this.major;
    }

    public int minor() {
        return this.minor;
    }

    @Nullable
    public static NvidiaDriverVersion tryParse(GLContextInfo driver) {
        if (!Objects.equals(driver.vendor(), "NVIDIA Corporation")) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(driver.version());

        if (!matcher.matches()) {
            return null;
        }

        int major, minor;

        try {
            major = Integer.parseInt(matcher.group("major"));
            minor = Integer.parseInt(matcher.group("minor"));
        } catch (NumberFormatException e) {
            return null;
        }

        return new NvidiaDriverVersion(major, minor);
    }

    /**
     * @param oldest The oldest version (inclusive) to test against
     * @param newest The newest version (exclusive) to test against
     * @return True if this version is within the specified version range
     */
    public boolean isWithinRange(NvidiaDriverVersion oldest, NvidiaDriverVersion newest) {
        return this.asInteger() >= oldest.asInteger() && this.asInteger() < newest.asInteger();
    }

    private long asInteger() {
        Validate.isTrue(this.major >= 0);
        Validate.isTrue(this.minor >= 0);

        return (Integer.toUnsignedLong(this.major) << 32) | (Integer.toUnsignedLong(this.minor) << 0);
    }
}
