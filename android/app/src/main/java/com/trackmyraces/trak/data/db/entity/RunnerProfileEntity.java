package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import androidx.annotation.NonNull;

/**
 * Local copy of the runner's profile.
 * Synced from/to the backend profile endpoint.
 */
@Entity(tableName = "runner_profile")
public class RunnerProfileEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "email")
    public String email;

    /** ISO date string YYYY-MM-DD — used to compute age at race */
    @ColumnInfo(name = "date_of_birth")
    public String dateOfBirth;

    /** "M", "F", "NB", or "prefer_not_to_say" */
    @ColumnInfo(name = "gender")
    public String gender;

    @ColumnInfo(name = "city")
    public String city;

    @ColumnInfo(name = "state")
    public String state;

    @ColumnInfo(name = "country")
    public String country;

    /** "metric" (km) or "imperial" (miles) */
    @ColumnInfo(name = "preferred_units")
    public String preferredUnits;

    /** "celsius" or "fahrenheit" */
    @ColumnInfo(name = "preferred_temp_unit")
    public String preferredTempUnit;

    /** JWT token for authenticating API calls — stored locally only */
    @ColumnInfo(name = "auth_token")
    public String authToken;

    @ColumnInfo(name = "status")
    public String status;

    @ColumnInfo(name = "created_at")
    public String createdAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    /** Whether this profile has been synced to the backend */
    @ColumnInfo(name = "is_synced")
    public boolean isSynced;

    /**
     * Comma-separated running interest tags — used to filter discovery sites.
     * Valid values: road, trail, ultra, marathon, parkrun, triathlon, ocr, track, crosscountry
     * Example: "road,marathon,trail"
     */
    @ColumnInfo(name = "interests")
    public String interests;

    /** ISO timestamp of the last successful discovery run — used to rate-limit background polling. */
    @ColumnInfo(name = "last_discover_at")
    public String lastDiscoverAt;

    /** Cached count of pending matches — drives the dashboard notification chip. */
    @ColumnInfo(name = "pending_count", defaultValue = "0")
    public int pendingCount;

    /**
     * Device-local UUID generated on first profile save.
     * Used as the runner identifier for backend API calls without requiring auth.
     * Will be replaced by a server-issued userId once auth is implemented.
     */
    @ColumnInfo(name = "user_id")
    public String userId;

    /**
     * Runner's typical pace in seconds per mile — used to disambiguate distance
     * when a race has multiple distance options (e.g. "10K / 50K / 100K").
     * 0 means not set.  Example: 10 min/mile = 600.
     */
    @ColumnInfo(name = "target_pace_seconds_per_mile", defaultValue = "0")
    public int targetPaceSecondsPerMile;

    /** Returns interests as a list, never null. */
    public java.util.List<String> getInterestList() {
        if (interests == null || interests.isEmpty()) return new java.util.ArrayList<>();
        return java.util.Arrays.asList(interests.split(","));
    }
}
