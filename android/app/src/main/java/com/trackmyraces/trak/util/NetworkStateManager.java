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
 * NetworkStateManager — app-scoped LiveData<NetworkState> singleton.
 *
 * Emits CONNECTED_UNMETERED, CONNECTED_METERED, or OFFLINE in real time
 * using ConnectivityManager.NetworkCallback. Registers while observed,
 * unregisters when no observers remain (lifecycle-safe, no leaks).
 *
 * All ConnectivityManager IPC calls run on a background executor — they
 * must NOT run on the main thread.
 *
 * Usage (via NetworkAwareFragment — preferred):
 *   @Override
 *   protected void onNetworkStateChanged(NetworkState state) {
 *       setViewOnlineOnly(binding.btnExtract, state.isOnline());
 *   }
 *
 * Usage (direct, for non-fragment contexts):
 *   TrakApplication.getInstance().getNetworkStateManager()
 *       .observe(lifecycleOwner, state -> { ... });
 */
public class NetworkStateManager extends LiveData<NetworkState> {

    private final ConnectivityManager                  mConnectivityManager;
    private final ExecutorService                      mExecutor;
    private final ConnectivityManager.NetworkCallback  mNetworkCallback;

    public NetworkStateManager(Context context) {
        super(NetworkState.UNKNOWN);

        mConnectivityManager = (ConnectivityManager)
            context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mExecutor = Executors.newSingleThreadExecutor();

        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                mExecutor.execute(() -> postValue(resolveState()));
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                mExecutor.execute(() -> postValue(resolveState()));
            }

            @Override
            public void onLost(Network network) {
                mExecutor.execute(() -> postValue(resolveState()));
            }
        };
    }

    @Override
    protected void onActive() {
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
        mExecutor.execute(() -> postValue(resolveState()));
    }

    @Override
    protected void onInactive() {
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
    }

    private NetworkState resolveState() {
        Network active = mConnectivityManager.getActiveNetwork();
        if (active == null) return NetworkState.OFFLINE;

        NetworkCapabilities caps = mConnectivityManager.getNetworkCapabilities(active);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkState.OFFLINE;
        }

        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            return NetworkState.CONNECTED_UNMETERED;
        }
        return NetworkState.CONNECTED_METERED;
    }
}
