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
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.dto.DiscoverResponse;
import com.trackmyraces.trak.data.network.dto.DiscoverSiteResult;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;
import com.trackmyraces.trak.data.repository.SourcesRepository;
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
 * Two modes (controlled by input data):
 *
 *   Cheap check (extractResults=false, default):
 *     Confirms runner existence on each site; does NOT extract individual results.
 *     Rate-limited to once every 7 days (SharedPreferences).
 *     Scheduled via PollScheduler (weekly periodic work).
 *
 *   Full extraction (extractResults=true):
 *     Extracts all individual race results, with optional sinceDate for incremental updates.
 *     Rate-limited to once every 48 hours (SharedPreferences).
 *     Scheduled via PollScheduler.requestManualPoll() — immediate or delayed out-of-cycle.
 */
public class ResultPollWorker extends Worker {

    private static final String TAG        = "ResultPollWorker";
    public static final  String CHANNEL_ID = "trak_new_results";
    private static final int    NOTIF_ID   = 1001;

    /** 7 days in milliseconds — minimum gap between background cheap checks. */
    private static final long CHECK_GAP_MS   = 7L * 24 * 60 * 60 * 1000;

    public ResultPollWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean extractResults = getInputData().getBoolean(PollScheduler.DATA_EXTRACT_RESULTS, false);
        String  sinceDate      = getInputData().getString(PollScheduler.DATA_SINCE_DATE);
        if (sinceDate != null && sinceDate.isEmpty()) sinceDate = null;

        Log.d(TAG, "Poll starting — extractResults=" + extractResults
            + (sinceDate != null ? " sinceDate=" + sinceDate : ""));

        try {
            // ── 1. Load runner profile ────────────────────────────────────────
            android.app.Application app =
                (android.app.Application) getApplicationContext();

            RunnerProfileRepository profileRepo = new RunnerProfileRepository(app);
            RunnerProfileEntity profile = profileRepo.getProfileSync();

            if (profile == null || profile.name == null || profile.name.isEmpty()) {
                Log.d(TAG, "No profile — skipping poll");
                return Result.success();
            }

            // ── 2. Rate-limit check ──────────────────────────────────────────
            if (extractResults) {
                // Full extraction: enforce 48h gap via SharedPreferences timestamp
                long lastMs  = getLastExtractMs();
                long ageMs   = System.currentTimeMillis() - lastMs;
                if (lastMs > 0 && ageMs < PollScheduler.EXTRACT_GAP_MS) {
                    Log.d(TAG, "Full extraction ran " + (ageMs / 3_600_000L) + "h ago — skipping");
                    return Result.success();
                }
            } else {
                // Cheap check: enforce 7-day gap via SharedPreferences timestamp
                long lastMs = getLastCheckMs();
                long ageMs  = System.currentTimeMillis() - lastMs;
                if (lastMs > 0 && ageMs < CHECK_GAP_MS) {
                    Log.d(TAG, "Cheap check ran " + (ageMs / 3_600_000L) + "h ago — skipping");
                    return Result.success();
                }
            }

            SourcesRepository sourcesRepo = new SourcesRepository(app);
            List<String> sourceIds = sourcesRepo.getEnabledSourceGuidsSync();
            List<com.trackmyraces.trak.data.network.dto.CustomSourceEntry> customSources =
                sourcesRepo.getEnabledCustomSourcesSync();
            java.util.Map<String, Integer> lastKnownCounts =
                PollScheduler.getLastKnownCounts(getApplicationContext());

            // ── 3. Call /discover ─────────────────────────────────────────────
            RaceResultRepository repo = new RaceResultRepository(app);

            CountDownLatch latch = new CountDownLatch(1);
            final DiscoverResponse[] result = {null};
            final String[]           error  = {null};

            repo.discoverResults(
                profile.userId, sourceIds, customSources,
                profile.name, profile.dateOfBirth,
                extractResults, sinceDate, lastKnownCounts,
                new RaceResultRepository.RepositoryCallback<DiscoverResponse>() {
                    @Override public void onSuccess(DiscoverResponse r) {
                        result[0] = r; latch.countDown();
                    }
                    @Override public void onError(String message) {
                        error[0] = message; latch.countDown();
                    }
                });

            latch.await(60, TimeUnit.SECONDS);

            // ── 4. Stamp the appropriate rate-limit timestamp ─────────────────
            if (extractResults) {
                PollScheduler.stampLastExtract(getApplicationContext());
            } else {
                PollScheduler.stampLastCheck(getApplicationContext());
            }

            if (error[0] != null) {
                Log.w(TAG, "Discover call failed: " + error[0]);
                return Result.retry();
            }

            if (result[0] == null || result[0].sites == null) {
                Log.d(TAG, "No discover response");
                return Result.success();
            }

            // ── 5. Update stored site counts (always, so next run can compare) ─
            if (result[0].siteResultCounts != null && !result[0].siteResultCounts.isEmpty()) {
                PollScheduler.storeSiteCounts(getApplicationContext(), result[0].siteResultCounts);
            }

            // ── 6. noChange shortcut — backend confirmed nothing changed ───────
            if (result[0].noChange) {
                Log.d(TAG, "No change detected — skipping pending match update");
                return Result.success();
            }

            // ── 7. Persist found results as pending matches ───────────────────
            List<DiscoverSiteResult> foundSites = new ArrayList<>();
            List<String> foundNames = new ArrayList<>();
            for (DiscoverSiteResult site : result[0].sites) {
                if (site.found) {
                    foundSites.add(site);
                    foundNames.add(site.siteName);
                }
            }

            Log.d(TAG, "Poll complete — found on " + foundSites.size() + " site(s)"
                + (extractResults ? " (full extraction)" : " (cheap check)"));

            if (!foundSites.isEmpty()) {
                int countBefore = repo.getPendingMatchCountSync();
                repo.savePendingMatches(foundSites, profile.name);
                Thread.sleep(500);
                int countAfter = repo.getPendingMatchCountSync();

                if (countAfter > countBefore) {
                    sendNotification(foundNames, extractResults);
                }
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

    private void sendNotification(List<String> foundSites, boolean hasDetails) {
        Context ctx = getApplicationContext();

        String siteList = android.text.TextUtils.join(", ", foundSites);
        String body;
        if (hasDetails) {
            body = foundSites.size() == 1
                ? "New race results found on " + siteList + ". Tap to review."
                : "New race results found on " + foundSites.size() + " sites. Tap to review.";
        } else {
            body = foundSites.size() == 1
                ? "Results found on " + siteList + ". Tap to view details."
                : "Results found on " + foundSites.size() + " sites. Tap to view details.";
        }

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

    private long getLastExtractMs() {
        return getApplicationContext()
            .getSharedPreferences("trak_poll_prefs", Context.MODE_PRIVATE)
            .getLong(PollScheduler.KEY_LAST_EXTRACT_MS_PREF, 0L);
    }

    private long getLastCheckMs() {
        return getApplicationContext()
            .getSharedPreferences("trak_poll_prefs", Context.MODE_PRIVATE)
            .getLong(PollScheduler.KEY_LAST_CHECK_MS_PREF, 0L);
    }
}
