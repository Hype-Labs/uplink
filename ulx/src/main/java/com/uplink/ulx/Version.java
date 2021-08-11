package com.uplink.ulx;

/**
 * The Version class is used to aggregate static versioning utilities for the
 * SDK. It allows querying version info, which is read from the project's build
 * environment. The framework releases are numbered in three parts, with a
 * "major.minor.patch" format (semantic versioning).
 */
public class Version {

    /**
     * Private constructor prevents instantiation.
     */
    private Version() {
    }

    /**
     * This is a utility method that splits the version string into parts
     * and returns an array of strings. This is used internally by the other
     * methods to parse the versioning components.
     * @return An array of the three version components in string form.
     */
    private static String[] getValues() {
        return getVersionString().split("\\.");
    }

    /**
     * The version number corresponds to the major version times 100, plus
     * the minor. For instance, version 1.5 would yield a value of 105. Beta
     * versions, for which the major is zero, are indicated by the minor
     * version alone, so 0.5 gives 5.
     * @return The framework's version in numeric form.
     */
    public static int getVersion() {
        return getMajor() * 100 + getMinor();
    }

    /**
     * The version is given in a "major.minor.patch" format (semantic
     * versioning). These values could be individually queried using the
     * respective getMajor(), getMinor(), and getPatch(), which return each
     * value in numeric form.
     * @return The framework's full version number in string form.
     */
    public static String getVersionString() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Major versions indicate profound changes to the framework, such as new
     * major features, stability, and so on.
     * @return The framework's major version.
     */
    public static int getMajor() {
        return Integer.parseInt(getValues()[0]);
    }

    /**
     * The minor version indicates minor changes to the framework, including
     * new minor features.
     * @return The framework's minor version.
     */
    public static int getMinor() {
        return Integer.parseInt(getValues()[1]);
    }

    /**
     * The patch version corresponds to an approximation of the build count
     * for the current release, so it jumps a lot. Higher patch values should
     * indicate more bug fixes, although not necessarily more features.
     * @return The framework's patch version.
     */
    public static int getPatch() {
        if (getValues().length <= 2) {
            return 0;
        }
        return Integer.parseInt(getValues()[2].split("\\-")[0]);
    }
}
