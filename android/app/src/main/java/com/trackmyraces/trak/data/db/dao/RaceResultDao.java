package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.trackmyraces.trak.data.db.entity.RaceResultEntity;

import java.util.List;

/**
 * RaceResultDao
 *
 * All database queries for race results.
 * LiveData variants are observed by ViewModels — Room updates them automatically.
 * Plain List variants are for background sync operations.
 */
@Dao
public interface RaceResultDao {

    // ── Write ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(RaceResultEntity result);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplaceAll(List<RaceResultEntity> results);

    @Update
    void update(RaceResultEntity result);

    @Query("UPDATE race_result SET status = 'deleted', updated_at = :now WHERE id = :id")
    void softDelete(String id, String now);

    @Query("UPDATE race_result SET is_pr = :isPR, updated_at = :now WHERE id = :id")
    void updateIsPR(String id, boolean isPR, String now);

    @Query("UPDATE race_result SET is_synced = 1, updated_at = :now WHERE id = :id")
    void markSynced(String id, String now);

    // ── Core list queries (LiveData — observed by UI) ──────────────────────

    /** All active results, newest first */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' ORDER BY race_date DESC")
    LiveData<List<RaceResultEntity>> getAllActive();

    /** Results filtered by canonical distance, newest first */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND distance_canonical = :distanceCanonical ORDER BY race_date DESC")
    LiveData<List<RaceResultEntity>> getByDistance(String distanceCanonical);

    /** PR results only (one per canonical distance) */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND is_pr = 1 ORDER BY distance_meters ASC")
    LiveData<List<RaceResultEntity>> getPRs();

    /** All editions of one named race across years */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND race_name_canonical = :slug ORDER BY race_date DESC")
    LiveData<List<RaceResultEntity>> getByRaceName(String slug);

    /** Results within a year range */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND CAST(substr(race_date, 1, 4) AS INTEGER) BETWEEN :yearFrom AND :yearTo ORDER BY race_date DESC")
    LiveData<List<RaceResultEntity>> getByYearRange(int yearFrom, int yearTo);

    /** Results for a specific surface type */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND surface_type = :surface ORDER BY race_date DESC")
    LiveData<List<RaceResultEntity>> getBySurface(String surface);

    /** BQ-eligible marathon results */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND distance_canonical = 'marathon' ORDER BY finish_seconds ASC")
    LiveData<List<RaceResultEntity>> getMarathonResults();

    // ── Single result queries ──────────────────────────────────────────────

    @Query("SELECT * FROM race_result WHERE id = :id")
    LiveData<RaceResultEntity> getById(String id);

    @Query("SELECT * FROM race_result WHERE id = :id")
    RaceResultEntity getByIdSync(String id);

    // ── PR recalculation queries (sync — called from background) ──────────

    /** All active results for a canonical distance, fastest first — for PR recalc */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND distance_canonical = :distanceCanonical ORDER BY COALESCE(chip_seconds, finish_seconds) ASC")
    List<RaceResultEntity> getByDistanceForPRSync(String distanceCanonical);

    /** Clear all PR flags for a distance before recalculating */
    @Query("UPDATE race_result SET is_pr = 0 WHERE distance_canonical = :distanceCanonical AND status != 'deleted'")
    void clearPRFlags(String distanceCanonical);

    /** Active results within a distance range (meters) — for PR recalc after local claim */
    @Query("SELECT * FROM race_result WHERE status != 'deleted' AND distance_meters BETWEEN :minMeters AND :maxMeters ORDER BY COALESCE(chip_seconds, finish_seconds) ASC")
    List<RaceResultEntity> getActiveByDistanceRange(double minMeters, double maxMeters);

    // ── Sync queries ───────────────────────────────────────────────────────

    /** Results not yet synced to backend */
    @Query("SELECT * FROM race_result WHERE is_synced = 0 AND status != 'deleted'")
    List<RaceResultEntity> getUnsynced();

    @Query("SELECT * FROM race_result WHERE status != 'deleted'")
    List<RaceResultEntity> getAllActiveSync();

    // ── Stats queries (for dashboard) ─────────────────────────────────────

    @Query("SELECT COUNT(*) FROM race_result WHERE status != 'deleted'")
    LiveData<Integer> getTotalCount();

    @Query("SELECT COUNT(DISTINCT race_name_canonical) FROM race_result WHERE status != 'deleted'")
    LiveData<Integer> getUniqueRaceCount();

    @Query("SELECT SUM(distance_meters) FROM race_result WHERE status != 'deleted'")
    LiveData<Double> getTotalDistanceMeters();

    /** Average pace (seconds/km) across results that have pace data. */
    @Query("SELECT AVG(pace_per_km_seconds) FROM race_result WHERE status != 'deleted' AND pace_per_km_seconds > 0")
    LiveData<Double> getAveragePacePerKm();

    @Query("SELECT MIN(COALESCE(chip_seconds, finish_seconds)) FROM race_result WHERE status != 'deleted' AND distance_canonical = :distanceCanonical")
    LiveData<Integer> getFastestSecondsForDistance(String distanceCanonical);


    // ── Added for Dashboard ───────────────────────────────────────────────

    @Query("SELECT * FROM race_result WHERE status != 'deleted' ORDER BY race_date DESC LIMIT :limit")
    LiveData<List<RaceResultEntity>> getRecentResults(int limit);

    @Query("SELECT DISTINCT CAST(substr(race_date, 1, 4) AS INTEGER) FROM race_result WHERE status != 'deleted' ORDER BY race_date DESC")
    LiveData<List<Integer>> getActiveYears();
}
