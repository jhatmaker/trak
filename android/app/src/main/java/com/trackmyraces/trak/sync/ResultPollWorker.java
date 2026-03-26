package com.trackmyraces.trak.sync;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.TrakApplication;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.dto.DiscoverResponse;
import com.trackmyraces.trak.data.network.dto.DiscoverSiteResult;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;
import com.trackmyraces.trak.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ResultPollWorker
 *
 * Background WorkManager worker that searches configured running sites for
 * the runner's results. Sends a notification if any site returns a match.
 *
 * Scheduled via PollScheduler (daily or weekly) or run immediately via
 * PollScheduler.pollNow(). Requires network connectivity.
 */
public class ResultPollWorker extends Worker {

    private static final String TAG          = "ResultPollWorker";
    static final         String CHANNEL_ID   = "trak_new_results";
    private static final int    NOTIF_ID     = 1001;

    public ResultPollWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Result poll starting");

        try {
            // ── 1. Load runner profile ─────────────────────────────────────
            RunnerProfileRepository profileRepo =
                new RunnerProfileRepository(getApplicationContext());
            RunnerProfileEntity profile = profileRepo.getProfileSync();

            if (profile == null || profile.name == null || profile.name.isEmpty()) {
                Log.d(TAG, "No profile — skipping poll");
                return Result.success();
            }

            List<String> interests = profile.getInterestList();

            // ── 2. Call /discover ──────────────────────────────────────────
            RaceResultRepository repo =
                new RaceResultRepository((android.app.Application) getApplicationContext());

            CountDownLatch latch = new CountDownLatch(1);
            final DiscoverResponse[] result = {null};
            final String[]           error  = {null};

            repo.discoverResults(profile.name, profile.dateOfBirth, interests,
                new RaceResultRepository.RepositoryCallback<DiscoverResponse>() {
                    @Override public void onSuccess(DiscoverResponse r) {
                        result[0] = r;
                        latch.countDown();
                    }
                    @Override public void onError(String message) {
                        error[0] = message;
                        latch.countDown();
                    }
                });

            latch.await(60, TimeUnit.SECONDS);

            if (error[0] != null) {
                Log.w(TAG, "Discover call failed: " + error[0]);
                return Result.retry();
            }

            // ── 3. Check for found sites ───────────────────────────────────
            if (result[0] == null || result[0].sites == null) {
                Log.d(TAG, "No discover response");
                return Result.success();
            }

            List<String> foundSites = new ArrayList<>();
            for (DiscoverSiteResult site : result[0].sites) {
                if (site.found) foundSites.add(site.siteName);
            }

            Log.d(TAG, "Poll complete — found on " + foundSites.size() + " site(s)");

            // ── 4. Notify if anything was found ───────────────────────────
            if (!foundSites.isEmpty()) {
                sendNotification(foundSites);
            }

            return Result.success();

        } catch (InterruptedException e) {
            Log.e(TAG, "Poll interrupted", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Poll unexpected error", e);
            return Result.failure();
        }
    }

    private void sendNotification(List<String> foundSites) {
        Context ctx = getApplicationContext();

        String siteList = android.text.TextUtils.join(", ", foundSites);
        String body = foundSites.size() == 1
            ? "Results found on " + siteList + ". Tap to view."
            : "Results found on " + foundSites.size() + " sites including " + foundSites.get(0) + ". Tap to view.";

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New race results found")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, notification.build());
    }
}
