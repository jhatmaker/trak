package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.trackmyraces.trak.data.db.entity.SavedViewEntity;

import java.util.List;

@Dao
public interface SavedViewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SavedViewEntity view);

    @Update
    void update(SavedViewEntity view);

    @Query("SELECT * FROM saved_view WHERE id = :id")
    SavedViewEntity getById(String id);

    @Query("SELECT * FROM saved_view WHERE is_synced = 0 OR is_synced IS NULL ORDER BY created_at DESC")
    LiveData<List<SavedViewEntity>> getAll();

    @Query("SELECT * FROM saved_view ORDER BY created_at DESC")
    List<SavedViewEntity> getAllSync();

    @Query("DELETE FROM saved_view WHERE id = :id")
    void delete(String id);

    @Query("SELECT * FROM saved_view WHERE is_synced = 0")
    List<SavedViewEntity> getUnsynced();
}
