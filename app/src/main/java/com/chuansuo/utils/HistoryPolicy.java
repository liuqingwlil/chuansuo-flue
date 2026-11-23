package com.chuansuo.utils;

public final class HistoryPolicy {
    public static final String EXPIRATION_KEY = "setting_history_expiration";
    private static final double DEFAULT_DAYS = 7;

    private HistoryPolicy() { }

    public static long cutoffSeconds(String configuredDays, long nowSeconds) {
        double days;
        try {
            days = Double.parseDouble(configuredDays);
        } catch (NumberFormatException | NullPointerException e) {
            days = DEFAULT_DAYS;
        }
        if (!Double.isFinite(days) || days <= 0 || days > 36500) {
            days = DEFAULT_DAYS;
        }
        return nowSeconds - (long) (days * 86400);
    }
}
