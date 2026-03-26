package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

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
public class ResultSplitEntity {

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
