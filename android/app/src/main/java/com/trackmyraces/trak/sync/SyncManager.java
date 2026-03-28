package com.trackmyraces.trak.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.trackmyraces.trak.data.repository.RaceResultRepository;

import java.util.concurrent.TimeUnit;

/**
 * SyncManager
 *
 * Routes requests between the local Room DB (offline) and the backend (online).
 * Also schedules periodic background sync via WorkManager.
 *
 * Strategy:
 *   - All reads come from Room (instant, works offline)
 *   - Online writes go to backend first, then update Room on success
 *   - Offline writes (manual entry) go to Room with isSynced=false
 *   - WorkManager syncs pending records when connectivity returns
 */
public class SyncManager {

    private static final String TAG             = "SyncManager";
    private static final String SYNC_WORK_NAME  = "trak_periodic_sync";

    private final Context               mContext;
    private final RaceResultRepository  mResultRepo;

    public SyncManager(Context context) {
        mContext    = context.getApplicationContext();
        mResultRepo = new RaceResultRepository((android.app.Application) mContext);
    }

    // ── Connectivity ──────────────────────────────────────────────────────

    /**
     * Returns true if the device has an active internet connection.
     */
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)
            mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    // ── Sync triggers ─────────────────────────────────────────────────────

    /**
     * Trigger a full sync if online:
     *   1. Push any locally-claimed results (isSynced=false) to the backend.
     *   2. Pull all results from the backend to refresh the local cache.
     *
     * Step 1 is a no-op when there are no unsynced results or no auth token.
     * Safe to call from any thread — delegates to repository callbacks.
     */
    public void syncIfOnline(SyncCallback callback) {
        if (!isOnline()) {
            Log.d(TAG, "Offline — skipping sync");
            if (callback != null) callback.onComplete(false, "Offline");
            return;
        }

        Log.d(TAG, "Online — pushing unsynced results then pulling");

        // Step 1: push locally-claimed results to backend
        mResultRepo.pushUnsyncedResults(new RaceResultRepository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer pushed) {
                if (pushed > 0) Log.d(TAG, "Pushed " + pushed + " unsynced results");

                // Step 2: pull all results from backend
                mResultRepo.syncAllFromBackend(new RaceResultRepository.RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer count) {
                        Log.d(TAG, "Sync complete — " + count + " results pulled");
                        if (callback != null) callback.onComplete(true, count + " results synced");
                    }
                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Pull error: " + message);
                        // Push succeeded even if pull failed — report partial success
                        if (callback != null) callback.onComplete(pushed > 0, message);
                    }
                });
            }
            @Override
            public void onError(String message) {
                Log.w(TAG, "Push error (non-fatal): " + message);
                // Push failed — still attempt pull
                mResultRepo.syncAllFromBackend(new RaceResultRepository.RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer count) {
                        Log.d(TAG, "Pull complete — " + count + " results");
                        if (callback != null) callback.onComplete(true, count + " results synced");
                    }
                    @Override
                    public void onError(String err) {
                        Log.e(TAG, "Sync error: " + err);
                        if (callback != null) callback.onComplete(false, err);
                    }
                });
            }
        });
    }

    // ── WorkManager periodic sync ─────────────────────────────────────────

    /**
     * Schedule periodic background sync.
     * Runs every 6 hours when connected to any network.
     * Safe to call multiple times — uses KEEP policy to avoid duplicates.
     */
    public void schedulePeriodicSync() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest syncRequest =
            new PeriodicWorkRequest.Builder(TrakSyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag("trak_sync")
                .build();

        WorkManager.getInstance(mContext).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        );

        Log.d(TAG, "Periodic sync scheduled — every 6 hours when connected");
    }

    /**
     * Cancel the periodic background sync.
     * Called when the user logs out.
     */
    public void cancelPeriodicSync() {
        WorkManager.getInstance(mContext).cancelUniqueWork(SYNC_WORK_NAME);
        Log.d(TAG, "Periodic sync cancelled");
    }

    // ── Callback ──────────────────────────────────────────────────────────

    public interface SyncCallback {
        void onComplete(boolean success, String message);
    }
}
