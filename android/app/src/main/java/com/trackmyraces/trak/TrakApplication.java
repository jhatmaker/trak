package com.trackmyraces.trak;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.sync.SyncManager;
import com.trackmyraces.trak.util.NetworkMonitor;
import com.trackmyraces.trak.util.NetworkStateManager;

/**
 * TrakApplication
 *
 * Application singleton — initialises shared singletons exactly once.
 * Accessible via TrakApplication.getInstance() from anywhere.
 */
public class TrakApplication extends Application {

    private static final String TAG = "TrakApplication";

    private static TrakApplication sInstance;

    // Lazily initialised singletons
    private TrakDatabase   mDatabase;
    private ApiClient      mApiClient;
    private SyncManager    mSyncManager;
    private NetworkMonitor      mNetworkMonitor;
    private NetworkStateManager mNetworkStateManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "Trak application starting — package: " + getPackageName());

        // Eagerly init database on startup so it's ready on first use
        getDatabase();

        // Create notification channel for background result poll alerts
        NotificationChannel channel = new NotificationChannel(
            com.trackmyraces.trak.sync.ResultPollWorker.CHANNEL_ID,
            "New race results",
            NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notifies when new race results are found on your linked sites");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .createNotificationChannel(channel);
    }

    public static TrakApplication getInstance() {
        return sInstance;
    }

    /** Returns the singleton Room database, creating it on first call. */
    public synchronized TrakDatabase getDatabase() {
        if (mDatabase == null) {
            mDatabase = TrakDatabase.getInstance(this);
        }
        return mDatabase;
    }

    /** Returns the singleton Retrofit API client, creating it on first call. */
    public synchronized ApiClient getApiClient() {
        if (mApiClient == null) {
            mApiClient = new ApiClient(this);
        }
        return mApiClient;
    }

    /** Returns the singleton SyncManager, creating it on first call. */
    public synchronized SyncManager getSyncManager() {
        if (mSyncManager == null) {
            mSyncManager = new SyncManager(this);
        }
        return mSyncManager;
    }

    /** Returns the singleton NetworkMonitor, creating it on first call. */
    public synchronized NetworkMonitor getNetworkMonitor() {
        if (mNetworkMonitor == null) {
            mNetworkMonitor = new NetworkMonitor(this);
        }
        return mNetworkMonitor;
    }

    /** Returns the singleton NetworkStateManager, creating it on first call. */
    public synchronized NetworkStateManager getNetworkStateManager() {
        if (mNetworkStateManager == null) {
            mNetworkStateManager = new NetworkStateManager(this);
        }
        return mNetworkStateManager;
    }
}
