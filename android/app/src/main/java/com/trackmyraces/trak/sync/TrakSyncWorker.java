package com.trackmyraces.trak.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.trackmyraces.trak.TrakApplication;

/**
 * TrakSyncWorker
 *
 * WorkManager Worker that runs in the background (even when app is closed)
 * to keep the local Room DB up to date with the backend.
 *
 * Scheduled by SyncManager.schedulePeriodicSync() — every 6 hours on WiFi/cellular.
 * Also triggered manually by SyncManager.syncIfOnline() on app foreground.
 */
public class TrakSyncWorker extends Worker {

    private static final String TAG = "TrakSyncWorker";

    public TrakSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Background sync starting");

        try {
            // Use a CountDownLatch to wait for the async repo call to complete
            // WorkManager runs this on a background thread but the repo uses callbacks
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] success = {false};

            SyncManager syncManager = TrakApplication.getInstance().getSyncManager();
            syncManager.syncIfOnline((ok, message) -> {
                success[0] = ok;
                Log.d(TAG, "Background sync result: " + message);
                latch.countDown();
            });

            // Wait up to 60 seconds for sync to complete
            latch.await(60, java.util.concurrent.TimeUnit.SECONDS);

            if (success[0]) {
                Log.d(TAG, "Background sync succeeded");
                return Result.success();
            } else {
                Log.w(TAG, "Background sync failed — will retry");
                return Result.retry();
            }

        } catch (InterruptedException e) {
            Log.e(TAG, "Background sync interrupted", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Background sync unexpected error", e);
            return Result.failure();
        }
    }
}
