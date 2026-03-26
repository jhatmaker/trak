package com.trackmyraces.trak.sync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * PollScheduler
 *
 * Manages the WorkManager schedule for background result polling.
 * Schedule preference is stored in SharedPreferences (device-local).
 *
 * Schedules:
 *   "off"    — no background polling
 *   "daily"  — once every 24 hours (requires network)
 *   "weekly" — once every 7 days (requires network)
 */
public class PollScheduler {

    public static final String SCHEDULE_OFF    = "off";
    public static final String SCHEDULE_DAILY  = "daily";
    public static final String SCHEDULE_WEEKLY = "weekly";

    private static final String WORK_NAME_PERIODIC = "result_poll_periodic";
    private static final String WORK_NAME_ONCE     = "result_poll_once";
    private static final String PREF_FILE          = "trak_poll_prefs";
    private static final String KEY_SCHEDULE       = "schedule";

    private static final Constraints NETWORK_REQUIRED = new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build();

    /** Save schedule preference and (re)schedule WorkManager accordingly. */
    public static void setSchedule(Context ctx, String schedule) {
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCHEDULE, schedule).apply();

        WorkManager wm = WorkManager.getInstance(ctx.getApplicationContext());

        if (SCHEDULE_OFF.equals(schedule)) {
            wm.cancelUniqueWork(WORK_NAME_PERIODIC);
            return;
        }

        long intervalHours = SCHEDULE_DAILY.equals(schedule) ? 24 : 24 * 7;

        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                ResultPollWorker.class, intervalHours, TimeUnit.HOURS)
            .setConstraints(NETWORK_REQUIRED)
            .build();

        wm.enqueueUniquePeriodicWork(WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.REPLACE, work);
    }

    /** Returns the stored schedule, defaulting to "off". */
    public static String getSchedule(Context ctx) {
        return ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULE, SCHEDULE_OFF);
    }

    /** Enqueue an immediate one-time poll (requires network). */
    public static void pollNow(Context ctx) {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(ResultPollWorker.class)
            .setConstraints(NETWORK_REQUIRED)
            .build();
        WorkManager.getInstance(ctx.getApplicationContext())
            .enqueueUniqueWork(WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE, work);
    }
}
