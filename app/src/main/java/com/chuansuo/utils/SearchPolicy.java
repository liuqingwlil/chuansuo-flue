package com.chuansuo.utils;

/** Search defaults independent of a successful device location fix. */
public final class SearchPolicy {
    private SearchPolicy() { }

    public static String resolveCity(String configuredCity, String locatedCity) {
        if (configuredCity != null && !configuredCity.trim().isEmpty()) {
            return configuredCity.trim();
        }
        if (locatedCity != null && !locatedCity.trim().isEmpty()) {
            return locatedCity.trim();
        }
        // SDK requires a city. cityLimit=false still permits cross-city results.
        return "北京";
    }
}
