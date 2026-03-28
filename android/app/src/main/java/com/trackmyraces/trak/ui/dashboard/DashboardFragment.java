package com.trackmyraces.trak.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.trackmyraces.trak.ui.NetworkAwareFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.databinding.FragmentDashboardBinding;
import com.trackmyraces.trak.ui.results.RaceResultAdapter;
import com.trackmyraces.trak.util.TimeFormatter;

/**
 * DashboardFragment — home screen.
 *
 * Shows summary stats, PR board, and recent results.
 * Pull-to-refresh triggers a backend sync via DashboardViewModel.
 */
public class DashboardFragment extends NetworkAwareFragment {

    private FragmentDashboardBinding mBinding;
    private DashboardViewModel       mViewModel;
    private RaceResultAdapter        mPRAdapter;
    private RaceResultAdapter        mRecentAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentDashboardBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        setupRecyclerViews();
        observeViewModel();

        // Pull-to-refresh
        mBinding.swipeRefresh.setColorSchemeResources(R.color.trak_primary);
        mBinding.swipeRefresh.setOnRefreshListener(() -> mViewModel.refresh());

        mBinding.btnReviewPending.setOnClickListener(v ->
            Navigation.findNavController(requireView())
                .navigate(R.id.action_dashboard_to_pending));
    }

    private void setupRecyclerViews() {
        // PR board — horizontal scroll of PR cards
        mPRAdapter = new RaceResultAdapter(result -> navigateToDetail(result.id));
        mBinding.rvPrs.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        mBinding.rvPrs.setAdapter(mPRAdapter);

        // Recent results — vertical list
        mRecentAdapter = new RaceResultAdapter(result -> navigateToDetail(result.id));
        mBinding.rvRecent.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.rvRecent.setAdapter(mRecentAdapter);
    }

    private void observeViewModel() {
        // Stats
        mViewModel.totalRaceCount.observe(getViewLifecycleOwner(), count -> {
            mBinding.tvRaceCount.setText(count != null ? String.valueOf(count) : "0");
        });

        // Observe both distance and profile so re-formatting happens if pref changes mid-session
        mViewModel.totalDistanceMeters.observe(getViewLifecycleOwner(), meters ->
            updateDistanceDisplay(meters, mViewModel.profile.getValue()));

        mViewModel.averagePacePerKm.observe(getViewLifecycleOwner(), pace ->
            updateAvgPaceDisplay(pace, mViewModel.profile.getValue()));

        mViewModel.profile.observe(getViewLifecycleOwner(), profile -> {
            updateDistanceDisplay(mViewModel.totalDistanceMeters.getValue(), profile);
            updateAvgPaceDisplay(mViewModel.averagePacePerKm.getValue(), profile);
        });

        mViewModel.uniqueRaceCount.observe(getViewLifecycleOwner(), count -> {
            mBinding.tvUniqueRaces.setText(count != null ? String.valueOf(count) : "0");
        });

        // PR board
        mViewModel.prList.observe(getViewLifecycleOwner(), prs -> {
            mPRAdapter.submitList(prs);
        });

        // Recent results
        mViewModel.recentResults.observe(getViewLifecycleOwner(), results -> {
            mRecentAdapter.submitList(results);
            boolean empty = results == null || results.isEmpty();
            mBinding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            mBinding.rvRecent.setVisibility(empty ? View.GONE : View.VISIBLE);
            mBinding.rvPrs.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        // Pending matches banner
        mViewModel.pendingMatchCount.observe(getViewLifecycleOwner(), count -> {
            int n = count != null ? count : 0;
            if (n > 0) {
                mBinding.cardPendingMatches.setVisibility(View.VISIBLE);
                mBinding.tvPendingSubtitle.setText(
                    getResources().getQuantityString(R.plurals.pending_matches_subtitle, n, n));
            } else {
                mBinding.cardPendingMatches.setVisibility(View.GONE);
            }
        });

        // Sync state → drive SwipeRefreshLayout
        mViewModel.syncState.observe(getViewLifecycleOwner(), state -> {
            mBinding.swipeRefresh.setRefreshing(state == DashboardViewModel.SyncState.SYNCING);
        });
    }

    private void updateDistanceDisplay(Double meters, RunnerProfileEntity profile) {
        String unitPref = (profile != null && profile.preferredUnits != null)
            ? profile.preferredUnits : "imperial";
        if (meters == null || meters == 0) {
            mBinding.tvTotalDistance.setText(TimeFormatter.formatDistance(0.0, unitPref));
        } else {
            mBinding.tvTotalDistance.setText(TimeFormatter.formatDistance(meters, unitPref));
        }
    }

    private void updateAvgPaceDisplay(Double pacePerKmSeconds, RunnerProfileEntity profile) {
        if (pacePerKmSeconds == null || pacePerKmSeconds <= 0) {
            mBinding.tvAvgPace.setText("—");
            return;
        }
        int rounded = (int) Math.round(pacePerKmSeconds);
        boolean imperial = profile != null && "imperial".equalsIgnoreCase(profile.preferredUnits);
        mBinding.tvAvgPace.setText(imperial
            ? TimeFormatter.pacePerMile(rounded)
            : TimeFormatter.pacePerKm(rounded));
    }

    private void navigateToDetail(String resultId) {
        Bundle args = new Bundle();
        args.putString("resultId", resultId);
        Navigation.findNavController(requireView())
            .navigate(R.id.action_dashboard_to_detail, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
