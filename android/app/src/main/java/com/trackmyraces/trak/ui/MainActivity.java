package com.trackmyraces.trak.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.TrakApplication;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.databinding.ActivityMainBinding;
import com.trackmyraces.trak.ui.profile.ProfileViewModel;

/**
 * MainActivity — single Activity host for the entire app.
 *
 * Uses Navigation Component with a BottomNavigationView.
 * All screens are Fragments navigated via nav_graph.xml.
 *
 * Bottom nav destinations:
 *   Dashboard   — summary stats, recent results, PR highlights
 *   History     — full filterable/sortable results list
 *   Add Result  — URL entry + extraction flow
 *   Profile     — runner profile and settings
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding mBinding;
    private NavController       mNavController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        setSupportActionBar(mBinding.toolbar);

        // Set up Navigation Component
        // Must use NavHostFragment.findNavController() when container is FragmentContainerView —
        // Navigation.findNavController(activity, viewId) fails in that case.
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        mNavController = navHostFragment.getNavController();

        // Top-level destinations — back button won't show on these
        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
            R.id.dashboardFragment,
            R.id.historyFragment,
            R.id.addResultFragment,
            R.id.profileFragment
        ).build();

        // Wire toolbar and bottom nav to nav controller
        NavigationUI.setupActionBarWithNavController(this, mNavController, appBarConfig);
        NavigationUI.setupWithNavController(mBinding.bottomNav, mNavController);

        // Observe network state — show/hide offline banner
        TrakApplication.getInstance().getNetworkMonitor().observe(this, isOnline ->
            mBinding.offlineBanner.setVisibility(isOnline ? View.GONE : View.VISIBLE)
        );

        // Profile gate: on fresh launch, navigate to Profile if no profile is set.
        // Uses a one-shot observer so it doesn't fire again after the user saves.
        // savedInstanceState != null means this is a config change (rotation) — skip.
        if (savedInstanceState == null) {
            ProfileViewModel profileVm = new ViewModelProvider(this).get(ProfileViewModel.class);
            Observer<RunnerProfileEntity>[] holder = new Observer[1];
            holder[0] = profile -> {
                profileVm.profile.removeObserver(holder[0]);
                if (profile == null || profile.name == null || profile.name.isEmpty()) {
                    mNavController.navigate(R.id.profileFragment);
                }
            };
            profileVm.profile.observe(this, holder[0]);
        }

        // Kick off background sync on launch
        TrakApplication.getInstance().getSyncManager().syncIfOnline((success, message) ->
            runOnUiThread(() -> {
                // Sync happens silently — no UI feedback needed on launch
            })
        );

        // Schedule periodic background sync
        TrakApplication.getInstance().getSyncManager().schedulePeriodicSync();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return mNavController.navigateUp() || super.onSupportNavigateUp();
    }
}
