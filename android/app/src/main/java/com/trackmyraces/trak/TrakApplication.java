package com.trackmyraces.trak;

import android.app.Application;
import android.util.Log;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.sync.SyncManager;

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

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "Trak application starting — package: " + getPackageName());

        // Eagerly init database on startup so it's ready on first use
        getDatabase();
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
}
