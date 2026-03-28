package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;

/**
 * RunnerProfileDao — single profile record for this device's runner.
 */
@Dao
public interface RunnerProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(RunnerProfileEntity profile);

    @Update
    void update(RunnerProfileEntity profile);

    /** There is only ever one active profile per device */
    @Query("SELECT * FROM runner_profile WHERE status != 'deleted' LIMIT 1")
    LiveData<RunnerProfileEntity> getProfile();

    @Query("SELECT * FROM runner_profile WHERE status != 'deleted' LIMIT 1")
    RunnerProfileEntity getProfileSync();

    @Query("SELECT auth_token FROM runner_profile WHERE status != 'deleted' LIMIT 1")
    String getAuthToken();

    @Query("UPDATE runner_profile SET auth_token = :token WHERE id = :id")
    void updateAuthToken(String id, String token);

    @Query("UPDATE runner_profile SET status = 'deleted' WHERE id = :id")
    void softDelete(String id);

    @Query("SELECT COUNT(*) FROM runner_profile WHERE status != 'deleted'")
    int getProfileCount();

    @Query("UPDATE runner_profile SET last_discover_at = :timestamp, pending_count = :count WHERE status != 'deleted'")
    void updateDiscoverStats(String timestamp, int count);

    @Query("UPDATE runner_profile SET last_synced_at = :timestamp WHERE status != 'deleted'")
    void updateLastSyncedAt(String timestamp);
}
