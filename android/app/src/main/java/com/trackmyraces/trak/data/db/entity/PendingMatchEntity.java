package com.trackmyraces.trak.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A candidate result found during discovery — waiting for the runner to confirm or dismiss.
 *
 * Lifecycle:
 *   discovered → status="pending"  (written by DiscoverFragment / ResultPollWorker)
 *   runner taps "Add to profile" → status="claimed" (AI extraction triggered)
 *   runner taps "Not me" → status="dismissed"
 *
 * deduplication_key is UNIQUE so re-polling the same site never creates duplicate rows.
 * Format: "{siteId}:{stableId}" e.g. "athlinks:12345678" or "ultrasignup:jane-smith-2024-boston"
 */
@Entity(
    tableName = "pending_match",
    indices = {
        @Index(value = {"deduplication_key"}, unique = true),
        @Index(value = {"status"}),
        @Index(value = {"discovered_at"}),
    }
)
public class PendingMatchEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /**
     * Stable unique key used to prevent duplicate rows across repeated discovery runs.
     * Format: "{siteId}:{stableId}" — e.g. "athlinks:98765432"
     */
    @ColumnInfo(name = "deduplication_key")
    public String deduplicationKey;

    /** Site identifier — matches DEFAULT_SITES id e.g. "athlinks", "ultrasignup" */
    @ColumnInfo(name = "site_id")
    public String siteId;

    /** Human-readable site name e.g. "Athlinks" */
    @ColumnInfo(name = "site_name")
    public String siteName;

    /** Runner name as found on the site */
    @ColumnInfo(name = "runner_name")
    public String runnerName;

    /** Direct URL to the results/athlete page on the source site */
    @ColumnInfo(name = "results_url")
    public String resultsUrl;

    /** Kept for backwards compatibility; always 1 for per-result rows. */
    @ColumnInfo(name = "result_count")
    public int resultCount;

    /** Any extra notes about this match. */
    @ColumnInfo(name = "notes")
    public String notes;

    /**
     * "pending"   — awaiting runner action
     * "claimed"   — runner confirmed and AI extraction was triggered
     * "dismissed" — runner said "not me"
     */
    @ColumnInfo(name = "status")
    public String status;

    /** ISO timestamp of when this match was discovered */
    @ColumnInfo(name = "discovered_at")
    public String discoveredAt;

    /** ISO timestamp of last status change */
    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    // ── Per-result detail fields (added in migration 8→9) ─────────────────────

    /** Full race name e.g. "2024 Boston Marathon". */
    @ColumnInfo(name = "race_name")
    public String raceName;

    /** Race date in YYYY-MM-DD format. */
    @ColumnInfo(name = "race_date")
    public String raceDate;

    /** Distance label as shown on the source site e.g. "Marathon", "10K". */
    @ColumnInfo(name = "distance_label")
    public String distanceLabel;

    /** Distance in metres — 0 if unknown. */
    @ColumnInfo(name = "distance_meters", defaultValue = "0")
    public double distanceMeters;

    /** "City, State" or "City, Country" — null if unknown. Kept for existing rows. */
    @ColumnInfo(name = "location")
    public String location;

    @ColumnInfo(name = "race_city")
    public String raceCity;

    @ColumnInfo(name = "race_state")
    public String raceState;

    @ColumnInfo(name = "race_country")
    public String raceCountry;

    /** Bib number as a string — null if unknown. */
    @ColumnInfo(name = "bib_number")
    public String bibNumber;

    /** Finish time string e.g. "3:45:22" — null if unknown. */
    @ColumnInfo(name = "finish_time")
    public String finishTime;

    /** Finish time in seconds — 0 if unknown. */
    @ColumnInfo(name = "finish_seconds", defaultValue = "0")
    public int finishSeconds;

    /** Overall finishing place — 0 if unknown. */
    @ColumnInfo(name = "overall_place", defaultValue = "0")
    public int overallPlace;

    /** Total finishers in the overall field — 0 if unknown. */
    @ColumnInfo(name = "overall_total", defaultValue = "0")
    public int overallTotal;

    /** Raw JSON from the source — shown via "View raw data" in the UI. */
    @ColumnInfo(name = "raw_data")
    public String rawData;
}
