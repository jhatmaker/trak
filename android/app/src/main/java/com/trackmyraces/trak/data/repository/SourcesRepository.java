package com.trackmyraces.trak.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.db.dao.UserSitePrefDao;
import com.trackmyraces.trak.data.db.entity.UserSitePrefEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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

    // ── Default site registry ─────────────────────────────────────────────────
    //
    // Stable GUIDs (00000000-0000-0000-0000-00000000000N) identify each default
    // site to the backend. These MUST stay in sync with defaultSites.js.
    // The siteId field (e.g. "athlinks") is the local DB key used in UserSitePrefEntity.

    public static class DefaultSiteInfo {
        public final String guid;
        public final String siteId;      // local DB key, matches backend site id
        public final String name;
        public final String description;

        public DefaultSiteInfo(String guid, String siteId, String name, String description) {
            this.guid        = guid;
            this.siteId      = siteId;
            this.name        = name;
            this.description = description;
        }
    }

    public static final List<DefaultSiteInfo> DEFAULT_SITES = Collections.unmodifiableList(Arrays.asList(
        new DefaultSiteInfo(
            "00000000-0000-0000-0000-000000000001", "athlinks",
            "Athlinks",
            "Largest race results aggregator — road, trail, triathlon, OCR, cycling"),
        new DefaultSiteInfo(
            "00000000-0000-0000-0000-000000000002", "ultrasignup",
            "Ultrasignup",
            "Ultra marathon and trail race results"),
        new DefaultSiteInfo(
            "00000000-0000-0000-0000-000000000003", "runsignup",
            "RunSignup",
            "Road race results from RunSignup-hosted events across the US"),
        new DefaultSiteInfo(
            "00000000-0000-0000-0000-000000000004", "nyrr",
            "New York Road Runners",
            "NYRR races including NYC Marathon, Queens 10K, and more"),
        new DefaultSiteInfo(
            "00000000-0000-0000-0000-000000000005", "baa",
            "Boston Athletic Association",
            "Boston Marathon and BAA road race results")
    ));

    // Total number of default sites
    public static final int TOTAL_DEFAULT_SITES = DEFAULT_SITES.size();

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

    /** Live count of enabled (non-hidden) custom sources — added to default count for button label. */
    public LiveData<Integer> getEnabledCustomSourceCount() {
        return mDao.getEnabledCustomSourceCount();
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

    /**
     * Returns the list of enabled source GUIDs for the current user.
     * For default sites, this is the GUID constant in DEFAULT_SITES.
     * For custom sources, the siteId (a UUID) doubles as the GUID.
     * Hidden sources are excluded.
     *
     * Must be called on a background thread.
     */
    public List<String> getEnabledSourceGuidsSync() {
        Set<String> hiddenSiteIds = new HashSet<>(mDao.getHiddenSiteIds());
        List<String> guids = new ArrayList<>();

        // Enabled default sites
        for (DefaultSiteInfo site : DEFAULT_SITES) {
            if (!hiddenSiteIds.contains(site.siteId)) {
                guids.add(site.guid);
            }
        }

        // Enabled custom sources — their siteId is a UUID that doubles as the GUID
        List<UserSitePrefEntity> all = mDao.getAllSync();
        for (UserSitePrefEntity pref : all) {
            if (pref.customUrl != null && !pref.hidden) {
                guids.add(pref.siteId);
            }
        }

        return guids;
    }

    /**
     * Returns the enabled custom sources for inclusion in a /discover request.
     * Only sources with a non-null customUrl and hidden=false are returned.
     * Must be called on a background thread.
     */
    public List<com.trackmyraces.trak.data.network.dto.CustomSourceEntry> getEnabledCustomSourcesSync() {
        List<UserSitePrefEntity> all = mDao.getAllSync();
        List<com.trackmyraces.trak.data.network.dto.CustomSourceEntry> result = new ArrayList<>();
        for (UserSitePrefEntity pref : all) {
            if (pref.customUrl != null && !pref.customUrl.isEmpty() && !pref.hidden) {
                result.add(new com.trackmyraces.trak.data.network.dto.CustomSourceEntry(
                    pref.siteId,
                    pref.customName != null ? pref.customName : pref.customUrl,
                    pref.customUrl
                ));
            }
        }
        return result;
    }

    /** Returns the GUID for a single default site by its siteId. */
    public static String getGuidForSiteId(String siteId) {
        for (DefaultSiteInfo site : DEFAULT_SITES) {
            if (site.siteId.equals(siteId)) return site.guid;
        }
        return siteId; // fallback: custom sources use siteId as GUID
    }

    public void addCustomSource(String name, String url) {
        mExecutor.execute(() -> {
            // Use UUID so the siteId doubles as the GUID for backend identification
            String siteId = UUID.randomUUID().toString();
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
