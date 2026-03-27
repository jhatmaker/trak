package com.trackmyraces.trak.util;

import java.util.Locale;

/**
 * TimeFormatter
 *
 * Shared display formatting for times, paces, and distances.
 * Pure static utility — no Android dependencies, fully unit-testable.
 */
public final class TimeFormatter {

    private TimeFormatter() {} // utility class

    /**
     * Format seconds as H:MM:SS or MM:SS.
     * e.g. 5025 → "1:23:45", 1335 → "22:15"
     */
    public static String secondsToTime(int seconds) {
        if (seconds <= 0) return "--:--";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    /**
     * Parse a time string to seconds.
     * Handles "H:MM:SS", "MM:SS", "H:MM:SS.d"
     * Returns -1 if unparseable.
     */
    public static int timeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return -1;
        String clean = timeStr.trim().split("\\.")[0]; // strip sub-seconds
        String[] parts = clean.split(":");
        try {
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                     + Integer.parseInt(parts[1]) * 60
                     + Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60
                     + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    /**
     * Format pace per km.
     * e.g. 267 seconds/km → "4:27/km"
     */
    public static String pacePerKm(int secondsPerKm) {
        if (secondsPerKm <= 0) return "--:--/km";
        int m = secondsPerKm / 60;
        int s = secondsPerKm % 60;
        return String.format(Locale.US, "%d:%02d/km", m, s);
    }

    /**
     * Format pace per mile.
     * e.g. 429 seconds/mile → "7:09/mi"
     */
    public static String pacePerMile(int secondsPerKm) {
        if (secondsPerKm <= 0) return "--:--/mi";
        int secondsPerMile = (int) Math.round(secondsPerKm * 1.60934);
        int m = secondsPerMile / 60;
        int s = secondsPerMile % 60;
        return String.format(Locale.US, "%d:%02d/mi", m, s);
    }

    /**
     * Format distance in metres to a human string.
     * unitPref: "imperial" → "26.2 mi", "metric" → "42.2 km",
     *           "both"     → "26.2 mi / 42.2 km"
     * Null/unknown unitPref defaults to imperial.
     */
    public static String formatDistance(double meters, String unitPref) {
        if (meters < 0) return "—";
        double miles = meters / 1609.344;
        double km    = meters / 1000.0;
        if ("metric".equals(unitPref)) {
            return String.format(Locale.US, "%.1f km", km);
        } else if ("both".equals(unitPref)) {
            return String.format(Locale.US, "%.1f mi / %.1f km", miles, km);
        } else {
            return String.format(Locale.US, "%.1f mi", miles);
        }
    }

    /**
     * Format distance in metres to a human string (legacy boolean overload).
     * @deprecated Use {@link #formatDistance(double, String)} with a unitPref string.
     */
    @Deprecated
    public static String formatDistance(double meters, boolean imperial) {
        return formatDistance(meters, imperial ? "imperial" : "metric");
    }

    /**
     * Format a BQ gap.
     * Positive = faster than standard, negative = slower.
     * e.g.  120 → "2:00 under BQ"
     *       -45 → "0:45 over BQ"
     */
    public static String formatBQGap(int gapSeconds) {
        int abs = Math.abs(gapSeconds);
        int m = abs / 60;
        int s = abs % 60;
        String time = String.format(Locale.US, "%d:%02d", m, s);
        return gapSeconds >= 0 ? time + " under BQ" : time + " over BQ";
    }

    /**
     * Format a percentile placing.
     * e.g. place=14, total=312 → "Top 96%"
     */
    public static String formatPlacePercentile(int place, int total) {
        if (place <= 0 || total <= 0) return "—";
        int pct = (int) Math.round((1.0 - (double) place / total) * 100);
        return "Top " + pct + "%";
    }

    /**
     * Format an age-grade percentage.
     * e.g. 68.4 → "68.4%"
     */
    public static String formatAgeGrade(double percent) {
        if (percent <= 0) return "—";
        return String.format(Locale.US, "%.1f%%", percent);
    }
}
