package com.trackmyraces.trak.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.db.dao.RaceResultDao;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.data.network.TrakApiService;
import com.trackmyraces.trak.data.network.dto.ExtractionRequest;
import com.trackmyraces.trak.data.network.dto.ExtractionResponse;
import com.trackmyraces.trak.data.network.dto.ClaimRequest;
import com.trackmyraces.trak.data.network.dto.ClaimResponse;

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
    private final TrakApiService   mApi;
    private final ExecutorService  mExecutor;

    public RaceResultRepository(Application application) {
        TrakDatabase db = TrakDatabase.getInstance(application);
        mDao      = db.raceResultDao();
        mApi      = new ApiClient(application).getService();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    // ── Read — all return LiveData observed by ViewModels ─────────────────

    public LiveData<List<RaceResultEntity>> getAllResults() {
        return mDao.getAllActive();
    }

    public LiveData<List<RaceResultEntity>> getResultsByDistance(String distanceCanonical) {
        return mDao.getByDistance(distanceCanonical);
    }

    public LiveData<List<RaceResultEntity>> getPRs() {
        return mDao.getPRs();
    }

    public LiveData<List<RaceResultEntity>> getResultsByRaceName(String slug) {
        return mDao.getByRaceName(slug);
    }

    public LiveData<List<RaceResultEntity>> getResultsByYearRange(int from, int to) {
        return mDao.getByYearRange(from, to);
    }


    public LiveData<List<RaceResultEntity>> getRecentResults(int limit) {
        return mDao.getRecentResults(limit);
    }

    public LiveData<Integer> getUniqueRaceCount() {
        return mDao.getUniqueRaceCount();
    }

    public LiveData<RaceResultEntity> getResultById(String id) {
        return mDao.getById(id);
    }

    public LiveData<Integer> getTotalCount() {
        return mDao.getTotalCount();
    }

    public LiveData<Double> getTotalDistanceMeters() {
        return mDao.getTotalDistanceMeters();
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

    // ── Delete ────────────────────────────────────────────────────────────

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
