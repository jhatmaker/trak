package com.trackmyraces.trak.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.trackmyraces.trak.data.db.entity.UserSitePrefEntity;

import java.util.List;

@Dao
public interface UserSitePrefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserSitePrefEntity pref);

    /** All pref rows — observed live so the Manage Sources list reacts to changes. */
    @Query("SELECT * FROM user_site_pref ORDER BY site_id ASC")
    LiveData<List<UserSitePrefEntity>> getAll();

    @Query("SELECT * FROM user_site_pref ORDER BY site_id ASC")
    List<UserSitePrefEntity> getAllSync();

    @Query("SELECT * FROM user_site_pref WHERE site_id = :siteId LIMIT 1")
    UserSitePrefEntity getById(String siteId);

    /** IDs of all sites the user has hidden — used to filter discover results. */
    @Query("SELECT site_id FROM user_site_pref WHERE hidden = 1")
    List<String> getHiddenSiteIds();

    /** Live count of hidden default sites (excludes custom sources). */
    @Query("SELECT COUNT(*) FROM user_site_pref WHERE hidden = 1 AND custom_url IS NULL")
    LiveData<Integer> getHiddenDefaultSiteCount();

    /** Live list of hidden default site IDs — for passing to the discover request. */
    @Query("SELECT site_id FROM user_site_pref WHERE hidden = 1 AND custom_url IS NULL")
    LiveData<List<String>> getHiddenDefaultSiteIdsLive();

    @Query("UPDATE user_site_pref SET hidden = :hidden WHERE site_id = :siteId")
    void setHidden(String siteId, boolean hidden);

    /** All user-added custom sources (not in DEFAULT_SITES). */
    @Query("SELECT * FROM user_site_pref WHERE custom_url IS NOT NULL ORDER BY added_at DESC")
    LiveData<List<UserSitePrefEntity>> getCustomSources();

    /** Live count of enabled (non-hidden) custom sources — for the poll button label. */
    @Query("SELECT COUNT(*) FROM user_site_pref WHERE hidden = 0 AND custom_url IS NOT NULL")
    LiveData<Integer> getEnabledCustomSourceCount();

    @Query("DELETE FROM user_site_pref WHERE site_id = :siteId AND custom_url IS NOT NULL")
    void deleteCustomSource(String siteId);
}
