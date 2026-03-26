package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.trackmyraces.trak.data.db.entity.CredentialEntryEntity;

import java.util.List;

@Dao
public interface CredentialEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CredentialEntryEntity credential);

    @Update
    void update(CredentialEntryEntity credential);

    @Query("SELECT * FROM credential_entry WHERE id = :id")
    CredentialEntryEntity getById(String id);

    @Query("SELECT * FROM credential_entry WHERE site_url = :siteUrl LIMIT 1")
    CredentialEntryEntity getBySiteUrl(String siteUrl);

    @Query("SELECT * FROM credential_entry ORDER BY site_label ASC")
    LiveData<List<CredentialEntryEntity>> getAll();

    @Query("SELECT * FROM credential_entry ORDER BY site_label ASC")
    List<CredentialEntryEntity> getAllSync();

    @Query("UPDATE credential_entry SET login_status = :status, updated_at = :updatedAt WHERE id = :id")
    void updateLoginStatus(String id, String status, String updatedAt);

    @Query("DELETE FROM credential_entry WHERE id = :id")
    void delete(String id);
}
