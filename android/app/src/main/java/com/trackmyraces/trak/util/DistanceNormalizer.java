package com.trackmyraces.trak.util;

import java.util.Locale;

/**
 * Maps raw distance labels and meter values to canonical keys and standard distances.
 *
 * Canonical keys match the backend's CANONICAL_DISTANCES in raceLogic.js:
 *   1mile, 5k, 8k, 10k, 15k, 10mile, halfmarathon, 25k, 30k, marathon,
 *   50k, 50mile, 100k, 100mile
 */
public final class DistanceNormalizer {

    // Parallel arrays: canonical key → standard meters
    public static final String[] KEYS = {
        "1mile", "5k", "8k", "10k", "15k", "10mile",
        "halfmarathon", "25k", "30k", "marathon",
        "50k", "50mile", "100k", "100mile"
    };
    public static final double[] METERS = {
        1609, 5000, 8047, 10000, 15000, 16093,
        21097, 25000, 30000, 42195,
        50000, 80467, 100000, 160934
    };

    private DistanceNormalizer() {}

    /**
     * Returns the canonical key for a distance label string (case-insensitive).
     * Returns null if the label cannot be matched.
     *
     * Examples:
     *   "Marathon"      → "marathon"
     *   "Half Marathon" → "halfmarathon"
     *   "10K"           → "10k"
     *   "50 Mile"       → "50mile"
     */
    public static String canonicalFromLabel(String label) {
        if (label == null || label.isEmpty()) return null;

        // Normalise: lowercase, strip punctuation/spaces
        String norm = label.toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9]", "");

        // Direct key match
        for (String key : KEYS) {
            if (norm.equals(key)) return key;
        }

        // Pattern matching for common variants
        if (norm.contains("marathon")) {
            if (norm.contains("half") || norm.contains("13") || norm.contains("21"))
                return "halfmarathon";
            if (norm.contains("ultra") || norm.contains("50") || norm.contains("100"))
                return null; // too ambiguous without explicit distance
            return "marathon";
        }
        if (norm.equals("100mile") || norm.equals("100miles") || norm.contains("100mi"))
            return "100mile";
        if (norm.equals("50mile") || norm.equals("50miles") || norm.contains("50mi"))
            return "50mile";
        if (norm.equals("10mile") || norm.equals("10miles") || norm.contains("10mi"))
            return "10mile";
        if (norm.equals("1mile")  || norm.equals("1miles")  || norm.contains("1mi"))
            return "1mile";
        if (norm.equals("100k") || norm.equals("100km") || norm.contains("100kil"))
            return "100k";
        if (norm.equals("50k")  || norm.equals("50km")  || norm.contains("50kil"))
            return "50k";
        if (norm.equals("30k")  || norm.equals("30km"))  return "30k";
        if (norm.equals("25k")  || norm.equals("25km"))  return "25k";
        if (norm.equals("15k")  || norm.equals("15km"))  return "15k";
        if (norm.equals("10k")  || norm.equals("10km"))  return "10k";
        if (norm.equals("8k")   || norm.equals("8km"))   return "8k";
        if (norm.equals("5k")   || norm.equals("5km"))   return "5k";

        return null;
    }

    /**
     * Returns the standard meters for a canonical key, or 0 if unknown.
     */
    public static double metersForKey(String key) {
        if (key == null) return 0;
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return METERS[i];
        }
        return 0;
    }

    /**
     * Returns the standard meters for a label string, or 0 if the label can't be matched.
     */
    public static double metersFromLabel(String label) {
        return metersForKey(canonicalFromLabel(label));
    }

    /**
     * Returns the canonical key for a given meter value, using ±5% tolerance.
     * Returns null if no canonical distance matches within tolerance.
     */
    public static String canonicalFromMeters(double meters) {
        if (meters <= 0) return null;
        double tolerance = meters * 0.05;
        for (int i = 0; i < METERS.length; i++) {
            if (Math.abs(METERS[i] - meters) <= tolerance) return KEYS[i];
        }
        return null;
    }

    /**
     * Resolves the best canonical key and meters from a (label, meters) pair.
     * Label takes priority for key; meters value is inferred from label when 0.
     *
     * @return String[2] — { canonicalKey, metersString } where either may be null/"0"
     */
    public static Result resolve(String label, double meters) {
        String key = canonicalFromLabel(label);
        double resolvedMeters = meters;

        if (resolvedMeters <= 0 && key != null) {
            resolvedMeters = metersForKey(key);
        }
        if (key == null && resolvedMeters > 0) {
            key = canonicalFromMeters(resolvedMeters);
        }

        return new Result(key, resolvedMeters);
    }

    public static class Result {
        public final String key;
        public final double meters;
        Result(String key, double meters) {
            this.key    = key;
            this.meters = meters;
        }
    }
}
