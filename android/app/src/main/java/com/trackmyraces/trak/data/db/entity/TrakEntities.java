package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

// ─────────────────────────────────────────────────────────────────────────────
// ResultSplitEntity
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Per-km/mile split for a race result.
 * Stored as a separate table; fetched with the result on the detail screen.
 */
@Entity(
    tableName = "result_split",
    indices = { @Index(value = {"result_id"}) },
    foreignKeys = @ForeignKey(
        entity = RaceResultEntity.class,
        parentColumns = "id",
        childColumns  = "result_id",
        onDelete      = ForeignKey.CASCADE
    )
)
class ResultSplitEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "result_id")
    public String resultId;

    /** Display label e.g. "5K", "Mile 13", "Halfway" */
    @ColumnInfo(name = "label")
    public String label;

    @ColumnInfo(name = "distance_meters")
    public double distanceMeters;

    /** Cumulative elapsed time from gun to this split */
    @ColumnInfo(name = "elapsed_seconds")
    public int elapsedSeconds;

    /** Time for this segment only (null if not available) */
    @ColumnInfo(name = "split_seconds")
    public Integer splitSeconds;

    @ColumnInfo(name = "split_place")
    public Integer splitPlace;
}

// ─────────────────────────────────────────────────────────────────────────────
// ResultClaimEntity
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A runner's claim that a particular result is theirs.
 * Created when the runner taps "Claim" on the extraction review screen.
 * status: "pending" | "confirmed" | "rejected" | "manual"
 */
@Entity(
    tableName = "result_claim",
    indices = { @Index(value = {"race_event_id"}), @Index(value = {"result_id"}) }
)
class ResultClaimEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "race_event_id")
    public String raceEventId;

    @ColumnInfo(name = "result_id")
    public String resultId;

    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "source_url")
    public String sourceUrl;

    /** True if result was entered manually rather than extracted */
    @ColumnInfo(name = "is_manual")
    public boolean isManual;

    @ColumnInfo(name = "claimed_at")
    public String claimedAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    @ColumnInfo(name = "is_synced")
    public boolean isSynced;
}

// ─────────────────────────────────────────────────────────────────────────────
// CredentialEntryEntity
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Metadata for a saved credential entry (club/timing site login).
 *
 * SECURITY: The password is NEVER stored here.
 * It is stored exclusively in Android Keystore via CredentialManager.
 * Only the Keystore alias is stored here so we can retrieve it.
 *
 * This entity IS synced to DynamoDB (username + site URL only — no password).
 */
@Entity(
    tableName = "credential_entry",
    indices = { @Index(value = {"site_url"}, unique = true) }
)
class CredentialEntryEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /** Base URL of the site e.g. "https://club.example.com" */
    @ColumnInfo(name = "site_url")
    public String siteUrl;

    /** Human-readable label e.g. "Quincy Running Club" */
    @ColumnInfo(name = "site_label")
    public String siteLabel;

    @ColumnInfo(name = "username")
    public String username;

    /**
     * Android Keystore alias for the encrypted password.
     * Use CredentialManager.getPassword(keystoreAlias) to retrieve.
     * Format: "trak_cred_{id}"
     */
    @ColumnInfo(name = "keystore_alias")
    public String keystoreAlias;

    /** "ok" | "expired" | "failed" | "untested" */
    @ColumnInfo(name = "login_status")
    public String loginStatus;

    @ColumnInfo(name = "last_login_at")
    public String lastLoginAt;

    /** ISO timestamp when the cached session cookie expires */
    @ColumnInfo(name = "cookie_expiry")
    public String cookieExpiry;

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;
}

// ─────────────────────────────────────────────────────────────────────────────
// SavedViewEntity
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A saved filter/sort preset that the runner can name and reuse.
 * e.g. "All my trail ultras", "Marathon PRs", "Last 3 years 5Ks"
 */
@Entity(tableName = "saved_view")
class SavedViewEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "name")
    public String name;

    /** "all" | "prs" | "byyear" | "byrace" | "bycanonical" | "bq" | "agegrade" | "custom" */
    @ColumnInfo(name = "view_type")
    public String viewType;

    /** Canonical distance key or "all" */
    @ColumnInfo(name = "distance")
    public String distance;

    /** "road" | "trail" | "track" | "xc" | "mixed" | "all" */
    @ColumnInfo(name = "surface")
    public String surface;

    @ColumnInfo(name = "year_from")
    public Integer yearFrom;

    @ColumnInfo(name = "year_to")
    public Integer yearTo;

    @ColumnInfo(name = "race_name_slug")
    public String raceNameSlug;

    /** "date" | "distance" | "finishTime" | "overallPlace" | "ageGrade" */
    @ColumnInfo(name = "sort")
    public String sort;

    /** "asc" | "desc" */
    @ColumnInfo(name = "order_dir")
    public String orderDir;

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    @ColumnInfo(name = "is_synced")
    public boolean isSynced;
}
