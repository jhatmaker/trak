package com.trackmyraces.trak.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Per-user preference record for a discovery source.
 *
 * One row per site the user has touched (hidden, shown, or added as custom).
 * Default sites with no row here are treated as visible and active.
 *
 * siteId values:
 *   - Matches a DEFAULT_SITES id (e.g. "athlinks") for built-in sites
 *   - "custom_1", "custom_2", ... for user-added sources
 */
@Entity(tableName = "user_site_pref")
public class UserSitePrefEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "site_id")
    public String siteId;

    /**
     * When true, this site is excluded from all discovery runs and not shown
     * in the sources list (unless "Show hidden" is toggled).
     */
    @ColumnInfo(name = "hidden", defaultValue = "0")
    public boolean hidden;

    /** Display name — null for default sites (use DEFAULT_SITES name), set for custom sources. */
    @ColumnInfo(name = "custom_name")
    public String customName;

    /** Base URL — null for default sites, set for user-added custom sources. */
    @ColumnInfo(name = "custom_url")
    public String customUrl;

    @ColumnInfo(name = "added_at")
    public String addedAt;

    /** Returns true if this is a user-added custom source (not in DEFAULT_SITES). */
    public boolean isCustom() {
        return customUrl != null;
    }
}
