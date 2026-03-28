package com.trackmyraces.trak.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.db.dao.PendingMatchDao;
import com.trackmyraces.trak.data.db.dao.RaceResultDao;
import com.trackmyraces.trak.data.db.dao.RunnerProfileDao;
import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.data.network.TrakApiService;
import com.trackmyraces.trak.data.network.dto.ClaimRequest;
import com.trackmyraces.trak.data.network.dto.ClaimResponse;
import com.trackmyraces.trak.data.network.dto.DiscoverRequest;
import com.trackmyraces.trak.util.DistanceNormalizer;
import com.trackmyraces.trak.util.OfflineCapability;
import com.trackmyraces.trak.data.network.dto.DiscoverResponse;
import com.trackmyraces.trak.data.network.dto.DiscoverSiteResult;
import com.trackmyraces.trak.data.network.dto.ExtractionRequest;
import com.trackmyraces.trak.data.network.dto.ExtractionResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * RaceResultRepository
 *
 * Single source of truth for race result data.
 * - UI reads from Room DB via LiveData (always instant, works offline)
 * - Network calls update Room, which propagates to UI automatically
 * - All DB writes happen on a background executor thread
 *
 * Usage from ViewModel:
 *   LiveData<List<RaceResultEntity>> results = repository.getAllResults();
 *   repository.extractResult(url, name, null, null, callback);
 */
public class RaceResultRepository {

    private static final String TAG = "RaceResultRepository";

    private final RaceResultDao    mDao;
    private final PendingMatchDao  mPendingMatchDao;
    private final RunnerProfileDao mProfileDao;
    private final TrakApiService   mApi;
    private final ExecutorService  mExecutor;

    public RaceResultRepository(Application application) {
        TrakDatabase db = TrakDatabase.getInstance(application);
        mDao             = db.raceResultDao();
        mPendingMatchDao = db.pendingMatchDao();
        mProfileDao      = db.runnerProfileDao();
        mApi             = new ApiClient(application).getService();
        mExecutor        = Executors.newSingleThreadExecutor();
    }

    // ── Read — all return LiveData observed by ViewModels ─────────────────

    @OfflineCapability(available = true)
    public LiveData<List<RaceResultEntity>> getAllResults() {
        return mDao.getAllActive();
    }

    @OfflineCapability(available = true)
    public LiveData<List<RaceResultEntity>> getResultsByDistance(String distanceCanonical) {
        return mDao.getByDistance(distanceCanonical);
    }

    @OfflineCapability(available = true)
    public LiveData<List<RaceResultEntity>> getPRs() {
        return mDao.getPRs();
    }

    @OfflineCapability(available = true)
    public LiveData<List<RaceResultEntity>> getResultsByRaceName(String slug) {
        return mDao.getByRaceName(slug);
    }

    @OfflineCapability(available = true)
    public LiveData<List<RaceResultEntity>> getResultsByYearRange(int from, int to) {
        return mDao.getByYearRange(from, to);
    }


    @OfflineCapability(available = true)
    public LiveData<List<RaceResultEntity>> getRecentResults(int limit) {
        return mDao.getRecentResults(limit);
    }

    @OfflineCapability(available = true)
    public LiveData<Integer> getUniqueRaceCount() {
        return mDao.getUniqueRaceCount();
    }

    @OfflineCapability(available = true)
    public LiveData<RaceResultEntity> getResultById(String id) {
        return mDao.getById(id);
    }

    @OfflineCapability(available = true)
    public LiveData<Integer> getTotalCount() {
        return mDao.getTotalCount();
    }

    @OfflineCapability(available = true)
    public LiveData<Double> getTotalDistanceMeters() {
        return mDao.getTotalDistanceMeters();
    }

    @OfflineCapability(available = true)
    public LiveData<Double> getAveragePacePerKm() {
        return mDao.getAveragePacePerKm();
    }

    // ── Discovery (online only) ───────────────────────────────────────────

    /**
     * Search default running sites for a runner.
     * Public endpoint — no auth token required.
     */
    @OfflineCapability(available = false, reason = "Requires network — calls /discover endpoint")
    public void discoverResults(String userId,
                                List<String> sourceIds,
                                List<com.trackmyraces.trak.data.network.dto.CustomSourceEntry> customSources,
                                String runnerName, String dateOfBirth,
                                boolean extractResults,
                                String sinceDate,
                                java.util.Map<String, Integer> lastKnownCounts,
                                RepositoryCallback<DiscoverResponse> callback) {
        DiscoverRequest request = new DiscoverRequest();
        request.userId          = userId;
        request.sourceIds       = sourceIds;
        request.customSources   = customSources;
        request.runnerName      = runnerName;
        request.dateOfBirth     = dateOfBirth;
        request.extractResults  = extractResults;
        request.sinceDate       = sinceDate;
        request.lastKnownCounts = lastKnownCounts;

        mApi.discoverResults(request).enqueue(new Callback<DiscoverResponse>() {
            @Override
            public void onResponse(Call<DiscoverResponse> call, Response<DiscoverResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Discovery failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<DiscoverResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    // ── Pending matches ───────────────────────────────────────────────────

    /**
     * Persist found discovery sites as pending matches in Room.
     * Uses IGNORE conflict strategy — safe to call on every discovery run;
     * rows already acted on (claimed/dismissed) are never overwritten.
     *
     * Also stamps last_discover_at and updates the pending_count on the profile.
     *
     * @param foundSites  Only sites where found==true
     * @param runnerName  Matched runner name — used to build the deduplication key
     */
    public void savePendingMatches(List<DiscoverSiteResult> foundSites, String runnerName) {
        savePendingMatches(foundSites, runnerName, null);
    }

    /**
     * Persist found discovery sites as pending matches in Room.
     *
     * @param callback  Optional — called on the executor thread with the total pending count
     *                  after all inserts complete. The caller must marshal to the UI thread
     *                  before touching views.
     */
    public void savePendingMatches(List<DiscoverSiteResult> foundSites, String runnerName,
                                   @androidx.annotation.Nullable RepositoryCallback<Integer> callback) {
        mExecutor.execute(() -> {
            String timestamp = now();

            for (com.trackmyraces.trak.data.network.dto.DiscoverSiteResult site : foundSites) {
                boolean hasIndividualResults = site.results != null && !site.results.isEmpty();

                if (hasIndividualResults) {
                    // One row per individual race result
                    for (com.trackmyraces.trak.data.network.dto.DiscoverResultRecord rec : site.results) {
                        PendingMatchEntity match = new PendingMatchEntity();
                        match.id = java.util.UUID.randomUUID().toString();
                        // Dedup key: prefer site-specific resultId; fall back to raceName+raceDate
                        String stableId = (rec.resultId != null && !rec.resultId.isEmpty())
                            ? rec.resultId
                            : normalizeForKey(rec.raceName) + "_" + (rec.raceDate != null ? rec.raceDate : "unknown");
                        match.deduplicationKey = site.siteId + ":" + stableId;
                        match.siteId           = site.siteId;
                        match.siteName         = site.siteName;
                        match.runnerName       = runnerName;
                        match.resultsUrl       = rec.resultsUrl != null ? rec.resultsUrl : site.resultsUrl;
                        match.resultCount      = 1;
                        match.status           = "pending";
                        match.discoveredAt     = timestamp;
                        match.updatedAt        = timestamp;
                        // Per-result detail fields
                        match.raceName         = rec.raceName;
                        match.raceDate         = rec.raceDate;
                        match.distanceLabel    = rec.distanceLabel;
                        match.distanceMeters   = rec.distanceMeters;
                        match.location         = rec.location;
                        match.raceCity         = rec.raceCity;
                        match.raceState        = rec.raceState;
                        match.raceCountry      = rec.raceCountry;
                        match.bibNumber        = rec.bibNumber;
                        match.finishTime       = rec.finishTime;
                        match.finishSeconds    = rec.finishSeconds;
                        match.overallPlace     = rec.overallPlace;
                        match.overallTotal     = rec.overallTotal;
                        mPendingMatchDao.insertIfAbsent(match);
                    }
                } else {
                    // Fallback: site says found=true but gave no individual results —
                    // store one placeholder row so the runner knows the site matched.
                    String normalizedName = normalizeForKey(runnerName);
                    PendingMatchEntity match = new PendingMatchEntity();
                    match.id               = java.util.UUID.randomUUID().toString();
                    match.deduplicationKey = site.siteId + ":" + normalizedName;
                    match.siteId           = site.siteId;
                    match.siteName         = site.siteName;
                    match.runnerName       = runnerName;
                    match.resultsUrl       = site.resultsUrl;
                    match.resultCount      = site.resultCount;
                    match.notes            = site.notes;
                    match.status           = "pending";
                    match.discoveredAt     = timestamp;
                    match.updatedAt        = timestamp;
                    mPendingMatchDao.insertIfAbsent(match);
                }
            }

            int pendingCount = mPendingMatchDao.getPendingCountSync();
            mProfileDao.updateDiscoverStats(timestamp, pendingCount);
            if (callback != null) callback.onSuccess(pendingCount);
        });
    }

    private static String normalizeForKey(String s) {
        if (s == null) return "unknown";
        return s.toLowerCase(java.util.Locale.US).trim().replaceAll("[^a-z0-9]+", "_");
    }

    @OfflineCapability(available = true)
    public LiveData<List<PendingMatchEntity>> getPendingMatches() {
        return mPendingMatchDao.getPending();
    }

    @OfflineCapability(available = true)
    public LiveData<Integer> getPendingMatchCount() {
        return mPendingMatchDao.getPendingCount();
    }

    @OfflineCapability(available = true)
    public void dismissPendingMatch(String matchId) {
        mExecutor.execute(() -> mPendingMatchDao.markDismissed(matchId, now()));
    }

    @OfflineCapability(available = true)
    public LiveData<List<PendingMatchEntity>> getDismissedMatches() {
        return mPendingMatchDao.getDismissed();
    }

    @OfflineCapability(available = true)
    public LiveData<List<PendingMatchEntity>> getDismissedMatchesForSite(String siteId) {
        return mPendingMatchDao.getDismissedForSite(siteId);
    }

    @OfflineCapability(available = true)
    public LiveData<Integer> getDismissedCountForSite(String siteId) {
        return mPendingMatchDao.getDismissedCountForSite(siteId);
    }

    /** Move a dismissed match back to pending so the runner can claim or dismiss it again. */
    @OfflineCapability(available = true)
    public void restorePendingMatch(String matchId) {
        mExecutor.execute(() -> mPendingMatchDao.restoreToPending(matchId, now()));
    }

    @OfflineCapability(available = true)
    public void claimPendingMatch(String matchId) {
        mExecutor.execute(() -> mPendingMatchDao.markClaimed(matchId, now()));
    }

    /**
     * Claims a pending match and immediately saves it as a RaceResultEntity in Room.
     * No API call — all data comes from the pending match itself (already extracted
     * by the discovery pipeline). The result is saved with isSynced=false so it
     * will be uploaded to the backend when auth is implemented.
     */
    @OfflineCapability(available = true)
    public void claimAndSave(PendingMatchEntity match) {
        mExecutor.execute(() -> {
            String timestamp = now();

            // Build a RaceResultEntity from the pending match data
            RaceResultEntity result = new RaceResultEntity();
            result.id              = java.util.UUID.randomUUID().toString();
            result.raceName        = match.raceName;
            result.raceDate        = match.raceDate;
            result.distanceLabel   = match.distanceLabel;

            // Resolve canonical key + infer meters from label when the value is 0
            DistanceNormalizer.Result dist =
                DistanceNormalizer.resolve(match.distanceLabel, match.distanceMeters);
            result.distanceCanonical = dist.key;
            result.distanceMeters    = dist.meters;

            result.finishTime      = match.finishTime;
            result.finishSeconds   = match.finishSeconds;
            result.overallPlace    = match.overallPlace > 0 ? match.overallPlace : null;
            result.overallTotal    = match.overallTotal > 0 ? match.overallTotal : null;
            result.bibNumber       = match.bibNumber;
            result.sourceUrl       = match.resultsUrl;
            result.notes           = match.notes;
            result.status          = "active";
            result.isSynced        = false;
            result.recordedAt      = timestamp;
            result.updatedAt       = timestamp;

            // Use separate location fields when available (newer records from Athlinks)
            if (match.raceCity != null || match.raceState != null || match.raceCountry != null) {
                result.raceCity    = match.raceCity;
                result.raceState   = match.raceState;
                result.raceCountry = match.raceCountry;
            } else if (match.location != null && match.location.contains(",")) {
                // Fallback: parse legacy combined "City, State" string
                String[] parts = match.location.split(",", 2);
                result.raceCity  = parts[0].trim();
                result.raceState = parts[1].trim();
            }

            // Canonical race name slug for grouping (e.g. "boston-marathon")
            if (match.raceName != null) {
                result.raceNameCanonical = match.raceName
                    .toLowerCase(java.util.Locale.US)
                    .replaceAll("\\b(20\\d{2})\\b", "")
                    .replaceAll("[^a-z0-9 ]", "")
                    .trim()
                    .replaceAll("\\s+", "-");
            }

            // Pace in seconds-per-km
            if (result.distanceMeters > 0 && result.finishSeconds > 0) {
                result.pacePerKmSeconds = (int) Math.round(
                    result.finishSeconds / (result.distanceMeters / 1000.0));
            }

            mDao.insertOrReplace(result);

            // Mark pending match as claimed
            mPendingMatchDao.markClaimed(match.id, timestamp);

            // Recalculate PR for this distance
            if (result.distanceMeters > 0) {
                recalculatePR(result.distanceMeters);
            }

            // Update pending count on profile
            int remaining = mPendingMatchDao.getPendingCountSync();
            mProfileDao.updateDiscoverStats(timestamp, remaining);
        });
    }

    /**
     * Marks the fastest result for a given distance as isPR=true, all others false.
     * Uses chip time when available, otherwise finish time.
     */
    private void recalculatePR(double distanceMeters) {
        // Allow ±5% tolerance for distance matching
        double tolerance = distanceMeters * 0.05;
        List<RaceResultEntity> results = mDao.getActiveByDistanceRange(
            distanceMeters - tolerance, distanceMeters + tolerance);
        if (results == null || results.isEmpty()) return;

        results.sort((a, b) -> Integer.compare(a.getBestSeconds(), b.getBestSeconds()));
        String timestamp = now();
        for (int i = 0; i < results.size(); i++) {
            mDao.updateIsPR(results.get(i).id, i == 0, timestamp);
        }
    }

    /**
     * One-time fix: backfill distanceMeters, distanceCanonical, and pacePerKmSeconds
     * on any existing claimed result that has those fields missing/zero.
     * Safe to call on every startup — only touches records that actually need it.
     */
    public void backfillDistanceAndPace() {
        mExecutor.execute(() -> {
            List<RaceResultEntity> all = mDao.getAllActiveSync();
            if (all == null || all.isEmpty()) return;

            String timestamp = now();
            for (RaceResultEntity r : all) {
                boolean changed = false;

                // Infer missing distance
                if (r.distanceMeters <= 0 || r.distanceCanonical == null) {
                    DistanceNormalizer.Result dist =
                        DistanceNormalizer.resolve(r.distanceLabel, r.distanceMeters);
                    if (dist.meters > 0 && r.distanceMeters <= 0) {
                        r.distanceMeters = dist.meters;
                        changed = true;
                    }
                    if (dist.key != null && r.distanceCanonical == null) {
                        r.distanceCanonical = dist.key;
                        changed = true;
                    }
                }

                // Compute missing pace
                if ((r.pacePerKmSeconds == null || r.pacePerKmSeconds <= 0)
                        && r.distanceMeters > 0 && r.finishSeconds > 0) {
                    r.pacePerKmSeconds = (int) Math.round(
                        r.finishSeconds / (r.distanceMeters / 1000.0));
                    changed = true;
                }

                if (changed) {
                    r.updatedAt = timestamp;
                    mDao.update(r);
                }
            }
        });
    }

    /** Synchronous pending count — for use in background workers only. */
    public int getPendingMatchCountSync() {
        return mPendingMatchDao.getPendingCountSync();
    }

    // ── Extraction (online only) ──────────────────────────────────────────

    /**
     * Ask the backend to extract a result from a URL.
     * No DB write here — runner must review and claim the result first.
     *
     * @param url           Results page URL
     * @param runnerName    Runner's name for matching
     * @param bibNumber     Optional bib number
     * @param cookie        Optional session cookie for credentialed sites
     * @param extraContext  Optional disambiguation hint
     * @param callback      Called on main thread with result or error
     */
    @OfflineCapability(available = false, reason = "Requires Anthropic API via /extract")
    public void extractResult(
            String url,
            String runnerName,
            String bibNumber,
            String cookie,
            String extraContext,
            RepositoryCallback<ExtractionResponse> callback) {

        ExtractionRequest request = new ExtractionRequest();
        request.url          = url;
        request.runnerName   = runnerName;
        request.bibNumber    = bibNumber;
        request.cookie       = cookie;
        request.extraContext = extraContext;

        mApi.extractResult(request).enqueue(new Callback<ExtractionResponse>() {
            @Override
            public void onResponse(Call<ExtractionResponse> call, Response<ExtractionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    String msg = "Extraction failed: HTTP " + response.code();
                    Log.e(TAG, msg);
                    callback.onError(msg);
                }
            }
            @Override
            public void onFailure(Call<ExtractionResponse> call, Throwable t) {
                Log.e(TAG, "Extraction network error", t);
                callback.onError(t.getMessage());
            }
        });
    }

    // ── Claim (online, then write to Room) ────────────────────────────────

    /**
     * Confirm a pending extraction as a claimed result.
     * On success, writes the result to Room DB and recalculates PRs.
     */
    @OfflineCapability(available = false, reason = "Requires backend — calls /claims")
    public void claimResult(
            String extractionId,
            Map<String, Object> edits,
            RepositoryCallback<ClaimResponse> callback) {

        ClaimRequest request = new ClaimRequest();
        request.extractionId = extractionId;
        request.edits        = edits;

        mApi.confirmClaim(request).enqueue(new Callback<ClaimResponse>() {
            @Override
            public void onResponse(Call<ClaimResponse> call, Response<ClaimResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ClaimResponse claimResponse = response.body();
                    // Fetch the full result and write to Room
                    syncResultFromBackend(claimResponse.resultId, () ->
                        callback.onSuccess(claimResponse)
                    );
                } else {
                    callback.onError("Claim failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<ClaimResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    // ── Manual entry (offline) ────────────────────────────────────────────

    /**
     * Save a manually-entered result directly to Room.
     * Does NOT call the backend — result is queued for sync later (isSynced=false).
     *
     * @param entity   Fully-populated entity (id must be set by caller)
     * @param callback Called on the executor thread with the saved resultId or error
     */
    @OfflineCapability(available = true)
    public void saveManualResult(RaceResultEntity entity, RepositoryCallback<String> callback) {
        mExecutor.execute(() -> {
            try {
                mDao.insertOrReplace(entity);
                if (entity.distanceCanonical != null) {
                    recalculatePRs(entity.distanceCanonical);
                }
                callback.onSuccess(entity.id);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save manual result", e);
                callback.onError(e.getMessage());
            }
        });
    }

    // ── Push locally-claimed results to backend ───────────────────────────

    /**
     * Push all Room results with isSynced=false to the backend.
     * Skips silently if there are no unsynced results or if the device is offline/unauthenticated.
     * Marks each result isSynced=true in Room on a successful push.
     *
     * Must NOT be called on the main thread — uses synchronous Retrofit execute().
     */
    @OfflineCapability(available = false, reason = "Requires backend — calls POST /results")
    public void pushUnsyncedResults(RepositoryCallback<Integer> callback) {
        mExecutor.execute(() -> {
            List<RaceResultEntity> unsynced = mDao.getUnsynced();
            if (unsynced == null || unsynced.isEmpty()) {
                callback.onSuccess(0);
                return;
            }

            int pushed = 0;
            for (RaceResultEntity r : unsynced) {
                try {
                    Map<String, Object> body = resultToMap(r);
                    retrofit2.Response<com.trackmyraces.trak.data.network.dto.ResultResponse> resp =
                        mApi.pushResult(body).execute();
                    if (resp.isSuccessful()) {
                        mDao.markSynced(r.id, now());
                        pushed++;
                        Log.d(TAG, "Pushed result " + r.id + " to backend");
                    } else if (resp.code() == 401) {
                        Log.d(TAG, "Not authenticated — stopping push at result " + r.id);
                        break;  // No point continuing — all calls will fail
                    } else {
                        Log.w(TAG, "Push failed for result " + r.id + ": HTTP " + resp.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Network error pushing result " + r.id, e);
                }
            }

            Log.d(TAG, "Pushed " + pushed + " of " + unsynced.size() + " unsynced results");
            callback.onSuccess(pushed);
        });
    }

    /** Converts a RaceResultEntity to a Map for the POST /results body. */
    private Map<String, Object> resultToMap(RaceResultEntity r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",                   r.id);
        m.put("claimId",              r.claimId);
        m.put("raceEventId",          r.raceEventId);
        m.put("raceName",             r.raceName);
        m.put("raceNameCanonical",    r.raceNameCanonical);
        m.put("raceDate",             r.raceDate);
        m.put("raceCity",             r.raceCity);
        m.put("raceState",            r.raceState);
        m.put("raceCountry",          r.raceCountry);
        m.put("distanceLabel",        r.distanceLabel);
        m.put("distanceCanonical",    r.distanceCanonical);
        m.put("distanceMeters",       r.distanceMeters);
        m.put("surfaceType",          r.surfaceType);
        m.put("isCertified",          r.isCertified);
        m.put("bibNumber",            r.bibNumber);
        m.put("finishTime",           r.finishTime);
        m.put("finishSeconds",        r.finishSeconds);
        m.put("chipTime",             r.chipTime);
        m.put("chipSeconds",          r.chipSeconds);
        m.put("pacePerKmSeconds",     r.pacePerKmSeconds);
        m.put("overallPlace",         r.overallPlace);
        m.put("overallTotal",         r.overallTotal);
        m.put("gender",               r.gender);
        m.put("genderPlace",          r.genderPlace);
        m.put("genderTotal",          r.genderTotal);
        m.put("ageGroupLabel",        r.ageGroupLabel);
        m.put("ageGroupCalc",         r.ageGroupCalc);
        m.put("ageGroupPlace",        r.ageGroupPlace);
        m.put("ageGroupTotal",        r.ageGroupTotal);
        m.put("ageAtRace",            r.ageAtRace);
        m.put("isBQ",                 r.isBQ);
        m.put("bqGapSeconds",         r.bqGapSeconds);
        m.put("ageGradePercent",      r.ageGradePercent);
        m.put("elevationGainMeters",  r.elevationGainMeters);
        m.put("elevationStartMeters", r.elevationStartMeters);
        m.put("temperatureCelsius",   r.temperatureCelsius);
        m.put("weatherCondition",     r.weatherCondition);
        m.put("notes",                r.notes);
        m.put("recordedAt",           r.recordedAt);
        return m;
    }

    // ── Update supplementary fields ───────────────────────────────────────

    /**
     * Save user edits to supplementary fields (location, bib, surface, conditions, notes).
     * Always writes locally; marks isSynced=false for later backend sync.
     */
    @OfflineCapability(available = true)
    public void updateResult(RaceResultEntity entity, RepositoryCallback<Void> callback) {
        mExecutor.execute(() -> {
            try {
                mDao.update(entity);
                callback.onSuccess(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update result", e);
                callback.onError(e.getMessage());
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @OfflineCapability(available = false, reason = "Requires backend — calls DELETE /results/{id}")
    public void deleteResult(String resultId, String claimId, RepositoryCallback<Void> callback) {
        // Optimistically soft-delete locally first
        mExecutor.execute(() -> {
            mDao.softDelete(resultId, now());
        });

        // Then delete on backend
        mApi.deleteClaim(claimId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    // Rollback local delete on failure
                    Log.e(TAG, "Backend delete failed: HTTP " + response.code() + " — local delete stands");
                    callback.onError("Delete failed on server — local copy removed");
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Delete network error", t);
                callback.onError(t.getMessage());
            }
        });
    }

    // ── Sync helpers ──────────────────────────────────────────────────────

    /**
     * Fetch a single result from the backend and write/update it in Room.
     * Called after claiming a result to get the fully-enriched server record.
     */
    private void syncResultFromBackend(String resultId, Runnable onComplete) {
        mApi.getResult(resultId).enqueue(new Callback<com.trackmyraces.trak.data.network.dto.ResultResponse>() {
            @Override
            public void onResponse(Call<com.trackmyraces.trak.data.network.dto.ResultResponse> call,
                                   Response<com.trackmyraces.trak.data.network.dto.ResultResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RaceResultEntity entity = response.body().toEntity();
                    entity.isSynced = true;
                    mExecutor.execute(() -> {
                        mDao.insertOrReplace(entity);
                        recalculatePRs(entity.distanceCanonical);
                        if (onComplete != null) onComplete.run();
                    });
                } else {
                    Log.e(TAG, "Failed to fetch result after claim: HTTP " + response.code());
                    if (onComplete != null) onComplete.run();
                }
            }
            @Override
            public void onFailure(Call<com.trackmyraces.trak.data.network.dto.ResultResponse> call, Throwable t) {
                Log.e(TAG, "Network error fetching result", t);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    /**
     * Recalculate PR flags for all results at a given canonical distance.
     * Must be called on a background thread.
     */
    public void recalculatePRs(String distanceCanonical) {
        mExecutor.execute(() -> {
            List<RaceResultEntity> results = mDao.getByDistanceForPRSync(distanceCanonical);
            mDao.clearPRFlags(distanceCanonical);
            String now = now();
            if (!results.isEmpty()) {
                // Results are sorted by best time ASC — first is the PR
                mDao.updateIsPR(results.get(0).id, true, now);
            }
            Log.d(TAG, "PR recalculated for distance: " + distanceCanonical
                + " — " + results.size() + " results checked");
        });
    }

    /**
     * Bulk sync: fetch all results from backend and upsert into Room.
     * Called by SyncManager during background sync.
     */
    @OfflineCapability(available = false, reason = "Requires backend — calls GET /results")
    public void syncAllFromBackend(RepositoryCallback<Integer> callback) {
        Map<String, String> filters = new HashMap<>();
        filters.put("limit", "200");

        mApi.getResults(filters).enqueue(new Callback<com.trackmyraces.trak.data.network.dto.ResultsListResponse>() {
            @Override
            public void onResponse(Call<com.trackmyraces.trak.data.network.dto.ResultsListResponse> call,
                                   Response<com.trackmyraces.trak.data.network.dto.ResultsListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<RaceResultEntity> entities = response.body().toEntities();
                    mExecutor.execute(() -> {
                        mDao.insertOrReplaceAll(entities);
                        Log.d(TAG, "Synced " + entities.size() + " results from backend");
                        callback.onSuccess(entities.size());
                    });
                } else {
                    callback.onError("Sync failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<com.trackmyraces.trak.data.network.dto.ResultsListResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.US).format(new java.util.Date());
    }

    // ── Callback interface ────────────────────────────────────────────────

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }
}
