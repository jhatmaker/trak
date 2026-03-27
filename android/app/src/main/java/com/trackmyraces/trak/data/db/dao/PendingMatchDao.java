package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;

import java.util.List;

/**
 * PendingMatchDao — access pending discovery matches.
 *
 * IGNORE conflict strategy on insert means re-polling the same site never overwrites a
 * match the runner has already acted on (claimed or dismissed).
 */
@Dao
public interface PendingMatchDao {

    /**
     * Insert a new match. If a row with the same deduplication_key already exists,
     * this is silently ignored — safe to call on every discovery run.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfAbsent(PendingMatchEntity match);

    /** All matches the runner hasn't acted on yet, newest first. */
    @Query("SELECT * FROM pending_match WHERE status = 'pending' ORDER BY discovered_at DESC")
    LiveData<List<PendingMatchEntity>> getPending();

    /** Synchronous version — used by background workers. */
    @Query("SELECT * FROM pending_match WHERE status = 'pending' ORDER BY discovered_at DESC")
    List<PendingMatchEntity> getPendingSync();

    /** How many pending matches exist — used for dashboard badge. */
    @Query("SELECT COUNT(*) FROM pending_match WHERE status = 'pending'")
    LiveData<Integer> getPendingCount();

    @Query("SELECT COUNT(*) FROM pending_match WHERE status = 'pending'")
    int getPendingCountSync();

    /** Mark a match as claimed after the runner confirms and AI extraction is triggered. */
    @Query("UPDATE pending_match SET status = 'claimed', updated_at = :updatedAt WHERE id = :id")
    void markClaimed(String id, String updatedAt);

    /** Mark a match as dismissed ("not me"). */
    @Query("UPDATE pending_match SET status = 'dismissed', updated_at = :updatedAt WHERE id = :id")
    void markDismissed(String id, String updatedAt);

    /** Get a single match by id. */
    @Query("SELECT * FROM pending_match WHERE id = :id")
    PendingMatchEntity getById(String id);

    /** Delete dismissed matches older than a given timestamp to keep the table tidy. */
    @Query("DELETE FROM pending_match WHERE status = 'dismissed' AND updated_at < :cutoff")
    void purgeDismissedBefore(String cutoff);
}
