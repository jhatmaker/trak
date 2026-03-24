package com.trackmyraces.trak.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.TrakApplication;
import com.trackmyraces.trak.databinding.ActivityMainBinding;

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

        // Set up Navigation Component
        mNavController = Navigation.findNavController(this, R.id.nav_host_fragment);

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
