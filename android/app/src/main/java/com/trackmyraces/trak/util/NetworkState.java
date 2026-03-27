package com.trackmyraces.trak.util;

/**
 * NetworkState — describes the current network connectivity.
 *
 * Used by NetworkStateManager and NetworkAwareFragment so fragments
 * react to connectivity changes without scattering if(isOnline) checks.
 */
public enum NetworkState {
    /** No active network connection. */
    OFFLINE,
    /** Connected via an unmetered network (Wi-Fi, Ethernet). */
    CONNECTED_UNMETERED,
    /** Connected via a metered network (mobile data). */
    CONNECTED_METERED,
    /** Initial state before first ConnectivityManager callback fires. */
    UNKNOWN;

    /** True when any network connection is available. */
    public boolean isOnline() {
        return this == CONNECTED_UNMETERED || this == CONNECTED_METERED;
    }

    /** True when connected via unmetered network (Wi-Fi / Ethernet). */
    public boolean isUnmetered() {
        return this == CONNECTED_UNMETERED;
    }
}
