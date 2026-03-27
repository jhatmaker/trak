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

    /** Approximate total number of results found on this site */
    @ColumnInfo(name = "result_count")
    public int resultCount;

    /**
     * Brief human-readable notes confirming the match — e.g. "Jane Smith, age 38, Boston MA".
     * Null for Athlinks direct-API results (not confirmed by AI).
     */
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
}
