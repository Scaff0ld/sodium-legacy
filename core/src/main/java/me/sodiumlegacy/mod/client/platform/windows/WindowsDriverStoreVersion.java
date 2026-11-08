package me.sodiumlegacy.mod.client.platform.windows;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WindowsDriverStoreVersion {
    private static final Pattern PATTERN = Pattern.compile(
            "^(?<driverModel>[0-9]{1,2})\\.(?<featureLevel>[0-9]{1,2})\\.(?<major>[0-9]{1,5})\\.(?<minor>[0-9]{1,5})$");

    private final int driverModel;
    private final int featureLevel;
    private final int major;
    private final int minor;

    public WindowsDriverStoreVersion(int driverModel, int featureLevel, int major, int minor) {
        this.driverModel = driverModel;
        this.featureLevel = featureLevel;
        this.major = major;
        this.minor = minor;
    }

    public int driverModel() {
        return this.driverModel;
    }

    public int featureLevel() {
        return this.featureLevel;
    }

    public int major() {
        return this.major;
    }

    public int minor() {
        return this.minor;
    }

    public static WindowsDriverStoreVersion parse(String version) throws ParseException {
        Matcher matcher = PATTERN.matcher(version);

        if (!matcher.matches()) {
            throw new ParseException(version);
        }

        int driverModel = Integer.parseInt(matcher.group("driverModel"));
        int featureLevel = Integer.parseInt(matcher.group("featureLevel"));
        int major = Integer.parseInt(matcher.group("major"));
        int minor = Integer.parseInt(matcher.group("minor"));

        return new WindowsDriverStoreVersion(driverModel, featureLevel, major, minor);
    }

    public String getFriendlyString() {
        return String.format("%s.%s.%s.%s", this.driverModel, this.featureLevel, this.major, this.minor);
    }

    public static class ParseException extends Exception {
        private ParseException(String version) {
            super(String.format("Not a valid driver store version (%s)", version));
        }
    }
}
