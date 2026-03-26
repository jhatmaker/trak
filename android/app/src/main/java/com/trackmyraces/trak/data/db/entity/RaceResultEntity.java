package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * A single claimed race result — the core entity of the app.
 *
 * Indexes on raceDate and distanceCanonical power the most common queries:
 *   - race history sorted by date
 *   - PR board filtered by canonical distance
 *   - race-over-years view filtered by raceNameCanonical
 */
@Entity(
    tableName = "race_result",
    indices = {
        @Index(value = {"race_date"}),
        @Index(value = {"distance_canonical"}),
        @Index(value = {"race_name_canonical"}),
        @Index(value = {"is_pr"}),
        @Index(value = {"claim_id"}, unique = true)
    }
)
public class RaceResultEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "claim_id")
    public String claimId;

    @ColumnInfo(name = "race_event_id")
    public String raceEventId;

    // ── Race identity ──────────────────────────────────────────────────────

    @ColumnInfo(name = "race_name")
    public String raceName;

    /** Normalised slug e.g. "boston-marathon" — used for race-over-years view */
    @ColumnInfo(name = "race_name_canonical")
    public String raceNameCanonical;

    /** ISO date YYYY-MM-DD */
    @ColumnInfo(name = "race_date")
    public String raceDate;

    @ColumnInfo(name = "race_city")
    public String raceCity;

    @ColumnInfo(name = "race_state")
    public String raceState;

    @ColumnInfo(name = "race_country")
    public String raceCountry;

    // ── Distance ──────────────────────────────────────────────────────────

    @ColumnInfo(name = "distance_label")
    public String distanceLabel;

    /** Canonical key e.g. "halfmarathon", "5k", "marathon" */
    @ColumnInfo(name = "distance_canonical")
    public String distanceCanonical;

    @ColumnInfo(name = "distance_meters")
    public double distanceMeters;

    /** "road", "trail", "track", "xc", "mixed" */
    @ColumnInfo(name = "surface_type")
    public String surfaceType;

    @ColumnInfo(name = "is_certified")
    public Boolean isCertified;

    // ── Timing ────────────────────────────────────────────────────────────

    @ColumnInfo(name = "bib_number")
    public String bibNumber;

    /** Display string e.g. "1:23:45" */
    @ColumnInfo(name = "finish_time")
    public String finishTime;

    /** Integer seconds — used for all comparisons and sorting */
    @ColumnInfo(name = "finish_seconds")
    public int finishSeconds;

    @ColumnInfo(name = "chip_time")
    public String chipTime;

    /** Preferred over finishSeconds for PR comparison when available */
    @ColumnInfo(name = "chip_seconds")
    public Integer chipSeconds;

    /** Seconds per km — used for pace display and trend charts */
    @ColumnInfo(name = "pace_per_km_seconds")
    public Integer pacePerKmSeconds;

    // ── Placement ─────────────────────────────────────────────────────────

    @ColumnInfo(name = "overall_place")
    public Integer overallPlace;

    @ColumnInfo(name = "overall_total")
    public Integer overallTotal;

    @ColumnInfo(name = "gender")
    public String gender;

    @ColumnInfo(name = "gender_place")
    public Integer genderPlace;

    @ColumnInfo(name = "gender_total")
    public Integer genderTotal;

    /** Age group label as returned by site e.g. "M40-44", "F35-39" */
    @ColumnInfo(name = "age_group_label")
    public String ageGroupLabel;

    /** Computed bracket e.g. "40-44" — may differ from site label */
    @ColumnInfo(name = "age_group_calc")
    public String ageGroupCalc;

    @ColumnInfo(name = "age_group_place")
    public Integer ageGroupPlace;

    @ColumnInfo(name = "age_group_total")
    public Integer ageGroupTotal;

    /** Runner's exact age on race day — computed from DOB + race date */
    @ColumnInfo(name = "age_at_race")
    public Integer ageAtRace;

    // ── Computed flags ────────────────────────────────────────────────────

    /** True if this is the fastest result for this canonical distance */
    @ColumnInfo(name = "is_pr")
    public boolean isPR;

    /** True if marathon time qualifies for Boston */
    @ColumnInfo(name = "is_bq")
    public boolean isBQ;

    /** Seconds faster (positive) or slower (negative) than BQ standard */
    @ColumnInfo(name = "bq_gap_seconds")
    public Integer bqGapSeconds;

    /** WMA age-graded performance percentage 0–100 */
    @ColumnInfo(name = "age_grade_percent")
    public Double ageGradePercent;

    // ── Race conditions ───────────────────────────────────────────────────

    /** Course elevation gain in meters — extracted by AI or from mapping data */
    @ColumnInfo(name = "elevation_gain_meters")
    public Integer elevationGainMeters;

    /** Race-day temperature in Celsius — fetched from Open-Meteo historical API */
    @ColumnInfo(name = "temperature_celsius")
    public Double temperatureCelsius;

    /** Human-readable weather description e.g. "Clear", "Rain", "Partly cloudy" */
    @ColumnInfo(name = "weather_condition")
    public String weatherCondition;

    // ── Meta ──────────────────────────────────────────────────────────────

    @ColumnInfo(name = "source_url")
    public String sourceUrl;

    @ColumnInfo(name = "notes")
    public String notes;

    /** "active" or "deleted" */
    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "recorded_at")
    public String recordedAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    /** True when synced to backend; false when created offline/not yet synced */
    @ColumnInfo(name = "is_synced")
    public boolean isSynced;

    // ── Convenience helpers ───────────────────────────────────────────────

    /** Returns chip_seconds if available, otherwise finish_seconds. Used for PR comparison. */
    public int getBestSeconds() {
        return (chipSeconds != null && chipSeconds > 0) ? chipSeconds : finishSeconds;
    }

    /** Returns percentage placing in overall field, or -1 if data unavailable. */
    public int getOverallPercentile() {
        if (overallPlace == null || overallTotal == null || overallTotal == 0) return -1;
        return (int) Math.round((1.0 - (double) overallPlace / overallTotal) * 100);
    }

    /** Returns formatted pace string e.g. "4:27/km" */
    public String getPaceDisplay() {
        if (pacePerKmSeconds == null || pacePerKmSeconds <= 0) return null;
        int m = pacePerKmSeconds / 60;
        int s = pacePerKmSeconds % 60;
        return String.format("%d:%02d/km", m, s);
    }

    /** Returns the race year as an integer, or 0 if unavailable. */
    public int getRaceYear() {
        if (raceDate == null || raceDate.length() < 4) return 0;
        try { return Integer.parseInt(raceDate.substring(0, 4)); }
        catch (NumberFormatException e) { return 0; }
    }
}
