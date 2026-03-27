package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.trackmyraces.trak.ui.NetworkAwareFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.databinding.FragmentHistoryBinding;

import java.util.List;

/**
 * HistoryFragment — full race result list with filter chips and sort menu.
 *
 * Observes ResultsViewModel.filteredResults — any filter change automatically
 * recomputes and updates the list without a network call.
 */
public class HistoryFragment extends NetworkAwareFragment {

    private FragmentHistoryBinding mBinding;
    private ResultsViewModel       mViewModel;
    private RaceResultAdapter      mAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentHistoryBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(ResultsViewModel.class);

        setupRecyclerView();
        setupFilterChips();
        setupToolbarMenu();
        observeViewModel();
    }

    private void setupRecyclerView() {
        mAdapter = new RaceResultAdapter(result -> {
            Bundle args = new Bundle();
            args.putString("resultId", result.id);
            Navigation.findNavController(requireView())
                .navigate(R.id.action_history_to_detail, args);
        });
        mBinding.rvResults.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.rvResults.setAdapter(mAdapter);
    }

    private void setupFilterChips() {
        // Distance chip — shows a dialog picker
        mBinding.chipDistance.setOnClickListener(v -> showDistancePicker());

        // Surface chip
        mBinding.chipSurface.setOnClickListener(v -> showSurfacePicker());

        // Year chip
        mBinding.chipYear.setOnClickListener(v -> showYearPicker());

        // PR only toggle chip
        mBinding.chipPrOnly.setOnCheckedChangeListener((chip, checked) ->
            mViewModel.setPROnlyFilter(checked));

        // Clear filters
        mBinding.tvClearFilters.setOnClickListener(v -> {
            mViewModel.clearAllFilters();
            resetChipLabels();
        });
    }

    private void showDistancePicker() {
        String[] options = {
            getString(R.string.filter_all_distances),
            "5K", "10K", "Half marathon", "Marathon",
            "50K", "50 mile", "100K", "100 mile"
        };
        String[] keys = { "all", "5k", "10k", "halfmarathon", "marathon", "50k", "50mile", "100k", "100mile" };

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.history_filter))
            .setItems(options, (dialog, which) -> {
                mViewModel.setDistanceFilter(keys[which]);
                mBinding.chipDistance.setText(options[which]);
                mBinding.chipDistance.setChecked(which != 0);
            })
            .show();
    }

    private void showSurfacePicker() {
        String[] options = {
            getString(R.string.filter_all_surfaces), "Road", "Trail", "Track", "XC"
        };
        String[] keys = { "all", "road", "trail", "track", "xc" };

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.history_filter))
            .setItems(options, (dialog, which) -> {
                mViewModel.setSurfaceFilter(keys[which]);
                mBinding.chipSurface.setText(options[which]);
                mBinding.chipSurface.setChecked(which != 0);
            })
            .show();
    }

    private void showYearPicker() {
        // Build year list from active years LiveData
        List<Integer> years = null; // populated from ViewModel in a real build
        // Fallback: last 10 years
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String[] options = new String[12];
        int[] yearValues = new int[12];
        options[0] = getString(R.string.filter_all_years);
        yearValues[0] = 0;
        for (int i = 1; i <= 11; i++) {
            options[i] = String.valueOf(currentYear - i + 1);
            yearValues[i] = currentYear - i + 1;
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.history_filter))
            .setItems(options, (dialog, which) -> {
                mViewModel.setYearFilter(yearValues[which]);
                mBinding.chipYear.setText(options[which]);
                mBinding.chipYear.setChecked(which != 0);
            })
            .show();
    }

    private void setupToolbarMenu() {
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_sort) {
                showSortDialog();
                return true;
            }
            return false;
        });
    }

    private void showSortDialog() {
        String[] sortOptions = { "Newest first", "Oldest first", "Fastest time", "Longest distance", "Best place" };
        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.history_sort))
            .setItems(sortOptions, (dialog, which) -> {
                switch (which) {
                    case 0: mViewModel.setSortBy("date");        mViewModel.sortAsc.setValue(false); break;
                    case 1: mViewModel.setSortBy("date");        mViewModel.sortAsc.setValue(true);  break;
                    case 2: mViewModel.setSortBy("finishTime");  mViewModel.sortAsc.setValue(true);  break;
                    case 3: mViewModel.setSortBy("distance");    mViewModel.sortAsc.setValue(false); break;
                    case 4: mViewModel.setSortBy("overallPlace");mViewModel.sortAsc.setValue(true);  break;
                }
            })
            .show();
    }

    private void observeViewModel() {
        mViewModel.filteredResults.observe(getViewLifecycleOwner(), results -> {
            mAdapter.submitList(results);

            boolean empty = results == null || results.isEmpty();
            mBinding.rvResults.setVisibility(empty ? View.GONE : View.VISIBLE);
            mBinding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

            int count = results != null ? results.size() : 0;
            mBinding.tvResultCount.setText(count + " result" + (count == 1 ? "" : "s"));

            mBinding.tvClearFilters.setVisibility(
                mViewModel.hasActiveFilters() ? View.VISIBLE : View.GONE);
        });
    }

    private void resetChipLabels() {
        mBinding.chipDistance.setText(R.string.filter_all_distances);
        mBinding.chipDistance.setChecked(false);
        mBinding.chipSurface.setText(R.string.filter_all_surfaces);
        mBinding.chipSurface.setChecked(false);
        mBinding.chipYear.setText(R.string.filter_all_years);
        mBinding.chipYear.setChecked(false);
        mBinding.chipPrOnly.setChecked(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
