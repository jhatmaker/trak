package com.trackmyraces.trak.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.trackmyraces.trak.TrakApplication;
import com.trackmyraces.trak.util.NetworkState;

/**
 * NetworkAwareFragment — base class for all Trak fragments.
 *
 * Observes NetworkStateManager and calls onNetworkStateChanged() whenever
 * connectivity changes. Subclasses override onNetworkStateChanged() once
 * to centralize all network-driven UI changes.
 *
 * Never extend Fragment directly — always extend NetworkAwareFragment.
 * Never scatter if(isOnline) checks — put all connectivity-driven UI logic
 * in a single onNetworkStateChanged() override.
 *
 * Example:
 *   public class MyFragment extends NetworkAwareFragment {
 *       @Override
 *       protected void onNetworkStateChanged(NetworkState state) {
 *           setViewOnlineOnly(binding.btnExtract, state.isOnline());
 *       }
 *   }
 */
public abstract class NetworkAwareFragment extends Fragment {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TrakApplication.getInstance()
            .getNetworkStateManager()
            .observe(getViewLifecycleOwner(),
                state -> onNetworkStateChanged(state != null ? state : NetworkState.UNKNOWN));
    }

    /**
     * Called on the main thread whenever network state changes, and once
     * immediately after onViewCreated with the current state.
     *
     * Default implementation is a no-op — override only when the fragment
     * has views that must be enabled/disabled based on connectivity.
     */
    protected void onNetworkStateChanged(@NonNull NetworkState state) {
        // no-op by default
    }

    /**
     * Enables or disables a view based on online status.
     * Sets alpha to 0.38 when disabled (Material Design disabled-state convention).
     *
     * @param view   the view to enable/disable
     * @param online true to enable, false to disable with reduced alpha
     */
    protected void setViewOnlineOnly(@NonNull View view, boolean online) {
        view.setEnabled(online);
        view.setAlpha(online ? 1.0f : 0.38f);
    }
}
