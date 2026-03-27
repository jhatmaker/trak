package com.trackmyraces.trak.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.db.dao.UserSitePrefDao;
import com.trackmyraces.trak.data.db.entity.UserSitePrefEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SourcesRepository — manages user site preferences (hide/show, custom sources).
 *
 * The list of discoverable sites has two layers:
 *   1. DEFAULT_SITES from the backend config — always available, never deleted
 *   2. user_site_pref rows — per-user hide flags and user-added custom sources
 *
 * The repository exposes LiveData so ManageSourcesFragment reacts in real time.
 */
public class SourcesRepository {

    private final UserSitePrefDao mDao;
    private final ExecutorService mExecutor;

    public SourcesRepository(Application application) {
        mDao      = TrakDatabase.getInstance(application).userSitePrefDao();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    // Total number of default sites (mirrors ManageSourcesFragment.DEFAULT_SITES.size())
    public static final int TOTAL_DEFAULT_SITES = 5;

    public LiveData<List<UserSitePrefEntity>> getAllPrefs() {
        return mDao.getAll();
    }

    public LiveData<List<UserSitePrefEntity>> getCustomSources() {
        return mDao.getCustomSources();
    }

    /** Live count of hidden default sites — used to compute the enabled count for the button label. */
    public LiveData<Integer> getHiddenDefaultSiteCount() {
        return mDao.getHiddenDefaultSiteCount();
    }

    /** Live list of hidden default site IDs — passed to /discover to skip those sites. */
    public LiveData<List<String>> getHiddenDefaultSiteIdsLive() {
        return mDao.getHiddenDefaultSiteIdsLive();
    }

    public void setHidden(String siteId, boolean hidden) {
        mExecutor.execute(() -> {
            // Ensure a row exists before updating
            UserSitePrefEntity existing = mDao.getById(siteId);
            if (existing == null) {
                UserSitePrefEntity pref = new UserSitePrefEntity();
                pref.siteId  = siteId;
                pref.hidden  = hidden;
                pref.addedAt = now();
                mDao.upsert(pref);
            } else {
                mDao.setHidden(siteId, hidden);
            }
        });
    }

    public void addCustomSource(String name, String url) {
        mExecutor.execute(() -> {
            // Generate a unique siteId for this custom source
            String siteId = "custom_" + System.currentTimeMillis();
            UserSitePrefEntity pref = new UserSitePrefEntity();
            pref.siteId     = siteId;
            pref.hidden      = false;
            pref.customName  = name.trim();
            pref.customUrl   = url.trim();
            pref.addedAt     = now();
            mDao.upsert(pref);
        });
    }

    public void deleteCustomSource(String siteId) {
        mExecutor.execute(() -> mDao.deleteCustomSource(siteId));
    }

    /** Returns the list of hidden site IDs synchronously — used by background workers. */
    public List<String> getHiddenSiteIdsSync() {
        return mDao.getHiddenSiteIds();
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
    }
}
