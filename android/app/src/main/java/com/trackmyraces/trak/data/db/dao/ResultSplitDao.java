package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.trackmyraces.trak.data.db.entity.ResultSplitEntity;

import java.util.List;

@Dao
public interface ResultSplitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ResultSplitEntity> splits);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ResultSplitEntity split);

    @Query("SELECT * FROM result_split WHERE result_id = :resultId ORDER BY distance_meters ASC")
    LiveData<List<ResultSplitEntity>> getSplitsForResult(String resultId);

    @Query("SELECT * FROM result_split WHERE result_id = :resultId ORDER BY distance_meters ASC")
    List<ResultSplitEntity> getSplitsForResultSync(String resultId);

    @Query("DELETE FROM result_split WHERE result_id = :resultId")
    void deleteForResult(String resultId);
}
