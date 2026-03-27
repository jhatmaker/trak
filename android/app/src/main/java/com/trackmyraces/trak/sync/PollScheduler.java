package com.trackmyraces.trak.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PollScheduler
 *
 * Manages the WorkManager schedule for background result polling.
 *
 * Background schedule (automatic):
 *   "off"    — no background polling
 *   "weekly" — cheap existence check once every 7 days (requires network)
 *              Does NOT extract individual results — minimal token cost.
 *
 * Out-of-cycle (user-triggered):
 *   "Search all sources now" runs a full extraction (extractResults=true).
 *   Rate-limited to once per 48 hours. If tapped within the 48h window, the
 *   poll is queued and fires automatically at the 48h mark via a delayed
 *   OneTimeWorkRequest — the button shows "Search scheduled" until then.
 */
public class PollScheduler {

    public static final String SCHEDULE_OFF    = "off";
    public static final String SCHEDULE_WEEKLY = "weekly";

    // WorkManager work names
    private static final String WORK_NAME_PERIODIC = "result_poll_periodic";
    private static final String WORK_NAME_MANUAL   = "result_poll_manual";   // immediate
    private static final String WORK_NAME_PENDING  = "result_poll_pending";  // delayed out-of-cycle

    // SharedPreferences file — shared with ResultPollWorker
    static final String PREF_FILE = "trak_poll_prefs";

    // SharedPreferences keys — package-visible so ResultPollWorker can read timestamps directly
    private static final String KEY_SCHEDULE           = "schedule";
    public  static final String KEY_LAST_EXTRACT_MS_PREF = "last_extract_at_ms";
    public  static final String KEY_LAST_CHECK_MS_PREF   = "last_check_at_ms";

    // Worker input-data keys (also read by ResultPollWorker)
    public static final String DATA_EXTRACT_RESULTS = "extractResults";
    public static final String DATA_SINCE_DATE      = "sinceDate";

    /** 48 hours in milliseconds — minimum gap between full extractions. */
    public static final long EXTRACT_GAP_MS = 48L * 60 * 60 * 1000;

    private static final Constraints NETWORK_REQUIRED = new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build();

    // ── Schedule preference ───────────────────────────────────────────────────

    /** Save schedule preference and (re)schedule WorkManager accordingly. */
    public static void setSchedule(Context ctx, String schedule) {
        prefs(ctx).edit().putString(KEY_SCHEDULE, schedule).apply();

        WorkManager wm = WorkManager.getInstance(ctx.getApplicationContext());

        if (SCHEDULE_OFF.equals(schedule)) {
            wm.cancelUniqueWork(WORK_NAME_PERIODIC);
            return;
        }

        // Weekly background cheap-check — no extractResults flag → defaults to false in worker
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                ResultPollWorker.class, 7, TimeUnit.DAYS)
            .setConstraints(NETWORK_REQUIRED)
            .build();

        wm.enqueueUniquePeriodicWork(WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP, work);
    }

    /** Returns the stored schedule, defaulting to "off". */
    public static String getSchedule(Context ctx) {
        return prefs(ctx).getString(KEY_SCHEDULE, SCHEDULE_OFF);
    }

    // ── Out-of-cycle manual poll ──────────────────────────────────────────────

    /**
     * Called when the user taps "Search all sources now".
     *
     * If the last full extraction was more than 48 hours ago (or never ran):
     *   → enqueues an immediate OneTimeWorkRequest (full extraction).
     *   → returns POLL_NOW so the caller can navigate to DiscoverFragment.
     *
     * If the last full extraction was within the last 48 hours:
     *   → schedules a delayed OneTimeWorkRequest to fire at the 48h mark.
     *   → returns POLL_SCHEDULED so the caller can show the "Search scheduled" state.
     *
     * @param sinceDate  YYYY-MM-DD to pass as incremental filter; null for first run.
     * @return {@link PollDecision#NOW} or {@link PollDecision#SCHEDULED}
     */
    public static PollDecision requestManualPoll(Context ctx, String sinceDate) {
        long lastExtractMs = prefs(ctx).getLong(KEY_LAST_EXTRACT_MS_PREF, 0L);
        long now           = System.currentTimeMillis();
        long ageMs         = now - lastExtractMs;

        WorkManager wm = WorkManager.getInstance(ctx.getApplicationContext());

        Data inputData = new Data.Builder()
            .putBoolean(DATA_EXTRACT_RESULTS, true)
            .putString(DATA_SINCE_DATE, sinceDate != null ? sinceDate : "")
            .build();

        if (ageMs >= EXTRACT_GAP_MS) {
            // Ready — run immediately (caller navigates to DiscoverFragment)
            wm.cancelUniqueWork(WORK_NAME_PENDING); // clear any stale pending
            return PollDecision.NOW;
        } else {
            // Within 48h window — schedule to fire at the 48h mark
            long delayMs = EXTRACT_GAP_MS - ageMs;
            OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(ResultPollWorker.class)
                .setConstraints(NETWORK_REQUIRED)
                .setInputData(inputData)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build();
            wm.enqueueUniqueWork(WORK_NAME_PENDING, ExistingWorkPolicy.KEEP, work);
            return PollDecision.SCHEDULED;
        }
    }

    /**
     * Stamps the last full-extraction timestamp in SharedPreferences.
     * Called by ResultPollWorker after a successful full extraction run.
     */
    public static void stampLastExtract(Context ctx) {
        prefs(ctx).edit().putLong(KEY_LAST_EXTRACT_MS_PREF, System.currentTimeMillis()).apply();
    }

    /**
     * Stamps the last cheap-check timestamp in SharedPreferences.
     * Called by ResultPollWorker after a successful cheap check run.
     */
    public static void stampLastCheck(Context ctx) {
        prefs(ctx).edit().putLong(KEY_LAST_CHECK_MS_PREF, System.currentTimeMillis()).apply();
    }

    /**
     * Returns the epoch-ms timestamp of the next scheduled extraction,
     * or 0 if no extraction is pending.
     * Used by the UI to display "Search scheduled · runs at HH:mm".
     */
    public static long getNextScheduledExtractMs(Context ctx) {
        long lastExtractMs = prefs(ctx).getLong(KEY_LAST_EXTRACT_MS_PREF, 0L);
        if (lastExtractMs == 0L) return 0L;
        long nextMs = lastExtractMs + EXTRACT_GAP_MS;
        return nextMs > System.currentTimeMillis() ? nextMs : 0L;
    }

    /** LiveData of WorkInfo for the pending (delayed) poll — lets the UI observe scheduled state. */
    public static LiveData<List<WorkInfo>> getPendingPollWorkInfo(Context ctx) {
        return WorkManager.getInstance(ctx.getApplicationContext())
            .getWorkInfosForUniqueWorkLiveData(WORK_NAME_PENDING);
    }

    public enum PollDecision { NOW, SCHEDULED }

    // ── Per-site result count storage ─────────────────────────────────────────

    private static final String SITE_COUNT_PREFIX = "site_count_";

    /**
     * Stores the current result count for each site in SharedPreferences.
     * Called after a successful discovery run so the next cheap check can compare.
     */
    public static void storeSiteCounts(Context ctx, Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) return;
        SharedPreferences.Editor editor = prefs(ctx).edit();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            editor.putInt(SITE_COUNT_PREFIX + entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    /**
     * Returns the last known result counts for all sites previously stored,
     * keyed by siteId. Returns an empty map if nothing has been stored yet.
     */
    public static Map<String, Integer> getLastKnownCounts(Context ctx) {
        Map<String, ?> all = prefs(ctx).getAll();
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(SITE_COUNT_PREFIX) && entry.getValue() instanceof Integer) {
                String siteId = entry.getKey().substring(SITE_COUNT_PREFIX.length());
                counts.put(siteId, (Integer) entry.getValue());
            }
        }
        return counts;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }
}
