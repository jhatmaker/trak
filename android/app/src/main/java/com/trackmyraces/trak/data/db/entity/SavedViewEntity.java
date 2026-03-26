package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * A saved filter/sort preset that the runner can name and reuse.
 * e.g. "All my trail ultras", "Marathon PRs", "Last 3 years 5Ks"
 */
@Entity(tableName = "saved_view")
public class SavedViewEntity {

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
