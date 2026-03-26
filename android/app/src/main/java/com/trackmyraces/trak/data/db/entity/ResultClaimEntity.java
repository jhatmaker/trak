package com.trackmyraces.trak.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * A runner's claim that a particular result is theirs.
 * Created when the runner taps "Claim" on the extraction review screen.
 * status: "pending" | "confirmed" | "rejected" | "manual"
 */
@Entity(
    tableName = "result_claim",
    indices = { @Index(value = {"race_event_id"}), @Index(value = {"result_id"}) }
)
public class ResultClaimEntity {

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
