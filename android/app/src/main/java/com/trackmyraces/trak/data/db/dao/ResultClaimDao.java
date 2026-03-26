package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.trackmyraces.trak.data.db.entity.ResultClaimEntity;

import java.util.List;

@Dao
public interface ResultClaimDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ResultClaimEntity claim);

    @Update
    void update(ResultClaimEntity claim);

    @Query("SELECT * FROM result_claim WHERE id = :id")
    ResultClaimEntity getById(String id);

    @Query("SELECT * FROM result_claim WHERE result_id = :resultId LIMIT 1")
    ResultClaimEntity getByResultId(String resultId);

    @Query("SELECT * FROM result_claim WHERE status != 'deleted' ORDER BY claimed_at DESC")
    LiveData<List<ResultClaimEntity>> getAllActive();

    @Query("SELECT * FROM result_claim WHERE status = :status ORDER BY claimed_at DESC")
    LiveData<List<ResultClaimEntity>> getByStatus(String status);

    @Query("UPDATE result_claim SET status = 'deleted', is_synced = :isSynced WHERE id = :id")
    void softDelete(String id, boolean isSynced);

    @Query("SELECT * FROM result_claim WHERE is_synced = 0")
    List<ResultClaimEntity> getUnsynced();
}
