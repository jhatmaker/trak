package com.trackmyraces.trak.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.lifecycle.LiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NetworkMonitor — LiveData<Boolean> that posts true when online, false when offline.
 *
 * Registers a ConnectivityManager.NetworkCallback while observed; unregisters when no
 * observers remain (lifecycle-safe, no leaks).
 *
 * IMPORTANT: getActiveNetwork() and getNetworkCapabilities() are IPC calls to system_server
 * and must NOT run on the main thread. All connectivity checks are dispatched to a background
 * executor, results delivered via postValue() which is thread-safe.
 *
 * Usage:
 *   TrakApplication.getInstance().getNetworkMonitor()
 *       .observe(viewLifecycleOwner, isOnline -> { ... });
 */
public class NetworkMonitor extends LiveData<Boolean> {

    private final ConnectivityManager mConnectivityManager;
    private final ExecutorService     mExecutor = Executors.newSingleThreadExecutor();
    private final ConnectivityManager.NetworkCallback mNetworkCallback;

    public NetworkMonitor(Context context) {
        mConnectivityManager = (ConnectivityManager)
            context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                postValue(true);
            }

            @Override
            public void onLost(Network network) {
                // Another network may still be active — check off main thread
                mExecutor.execute(() -> postValue(hasActiveConnection()));
            }
        };
    }

    @Override
    protected void onActive() {
        // Register callback first so no state change is missed.
        // Then check current state on a background thread — avoids blocking main thread
        // with IPC calls to system_server (getActiveNetwork / getNetworkCapabilities).
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
        mExecutor.execute(() -> postValue(hasActiveConnection()));
    }

    @Override
    protected void onInactive() {
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
    }

    private boolean hasActiveConnection() {
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = mConnectivityManager.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
