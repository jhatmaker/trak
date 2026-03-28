package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.trackmyraces.trak.ui.NetworkAwareFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.databinding.FragmentResultDetailBinding;
import com.trackmyraces.trak.util.TimeFormatter;

/**
 * ResultDetailFragment — full result detail.
 *
 * Receives resultId as a navigation argument.
 * Observes a single RaceResultEntity from ResultDetailViewModel.
 */
public class ResultDetailFragment extends NetworkAwareFragment {

    private FragmentResultDetailBinding mBinding;
    private ResultDetailViewModel       mViewModel;
    private String                      mResultId;
    private boolean                     mMoreDetailsExpanded = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentResultDetailBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mResultId = null;
        if (getArguments() != null) mResultId = getArguments().getString("resultId");

        mViewModel = new ViewModelProvider(this,
            new ResultDetailViewModel.Factory(requireActivity().getApplication(), mResultId))
            .get(ResultDetailViewModel.class);

        mBinding.btnMoreDetails.setOnClickListener(v -> toggleMoreDetails());

        observeViewModel();
    }

    private void toggleMoreDetails() {
        mMoreDetailsExpanded = !mMoreDetailsExpanded;
        mBinding.cardMoreDetails.setVisibility(mMoreDetailsExpanded ? View.VISIBLE : View.GONE);
        mBinding.tvMoreDetailsChevron.setText(mMoreDetailsExpanded ? "▾" : "▸");
    }

    private void observeViewModel() {
        mViewModel.result.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            bindResult(result);
        });
    }

    private void bindResult(RaceResultEntity r) {
        // Hero header
        mBinding.tvRaceName.setText(r.raceName != null ? r.raceName : "");

        String dateDist = formatDate(r.raceDate)
            + (r.distanceLabel != null ? "  ·  " + r.distanceLabel : "")
            + (r.surfaceType != null && !r.surfaceType.equals("road")
               ? "  ·  " + capitalize(r.surfaceType) : "");
        mBinding.tvDateDist.setText(dateDist);

        // Big finish time
        String time = r.chipTime != null ? r.chipTime : r.finishTime;
        mBinding.tvFinishTime.setText(time != null ? time : "--:--");

        // Pace
        String pace = r.getPaceDisplay();
        mBinding.tvPace.setText(pace != null ? pace : "");
        mBinding.tvPace.setVisibility(pace != null ? View.VISIBLE : View.GONE);

        // Badges
        mBinding.badgePrDetail.setVisibility(r.isPR ? View.VISIBLE : View.GONE);
        mBinding.badgeBqDetail.setVisibility(r.isBQ ? View.VISIBLE : View.GONE);

        // Placement row — only show when at least overall place is known
        boolean hasPlacement = r.overallPlace != null && r.overallPlace > 0;
        mBinding.placementRow.setVisibility(hasPlacement ? View.VISIBLE : View.GONE);

        if (hasPlacement) {
            mBinding.tvOverall.setText(r.overallTotal != null
                ? getString(R.string.place_of, r.overallPlace, r.overallTotal)
                : String.valueOf(r.overallPlace));

            mBinding.tvGenderPlace.setText(r.genderPlace != null && r.genderPlace > 0
                ? (r.genderTotal != null ? getString(R.string.place_of, r.genderPlace, r.genderTotal) : String.valueOf(r.genderPlace))
                : "—");

            mBinding.tvAgPlace.setText(r.ageGroupPlace != null && r.ageGroupPlace > 0
                ? (r.ageGroupTotal != null ? getString(R.string.place_of, r.ageGroupPlace, r.ageGroupTotal) : String.valueOf(r.ageGroupPlace))
                : "—");

            mBinding.tvAgLabel.setText(r.ageGroupLabel != null ? r.ageGroupLabel
                : r.ageGroupCalc != null ? r.ageGroupCalc : getString(R.string.detail_age_group));
        }

        // Detail rows — hide chip time if null or identical to finish time
        boolean hasDistinctChipTime = r.chipTime != null
            && !r.chipTime.equals(r.finishTime);
        if (hasDistinctChipTime) {
            mBinding.tvChipTimeRow.setText(
                getString(R.string.detail_chip_time) + ":  " + r.chipTime);
            mBinding.tvChipTimeRow.setVisibility(View.VISIBLE);
        } else {
            mBinding.tvChipTimeRow.setVisibility(View.GONE);
        }

        mBinding.tvAgeAtRaceRow.setText(
            getString(R.string.detail_age_at_race) + ":  "
            + (r.ageAtRace != null ? r.ageAtRace + " years old" : "—"));

        if (r.ageGradePercent != null && r.ageGradePercent > 0) {
            mBinding.tvAgeGradeRow.setText(
                getString(R.string.detail_age_grade) + ":  "
                + TimeFormatter.formatAgeGrade(r.ageGradePercent));
            mBinding.tvAgeGradeRow.setVisibility(View.VISIBLE);
        } else {
            mBinding.tvAgeGradeRow.setVisibility(View.GONE);
        }

        // BQ gap (marathon only)
        if (r.bqGapSeconds != null) {
            mBinding.tvBqGapRow.setText(
                getString(R.string.detail_bq_gap) + ":  "
                + TimeFormatter.formatBQGap(r.bqGapSeconds));
            mBinding.tvBqGapRow.setVisibility(View.VISIBLE);
            mBinding.dividerBq.setVisibility(View.VISIBLE);
        }

        // More details card
        bindMoreDetails(r);

        // Notes
        if (r.notes != null && !r.notes.isEmpty()) {
            mBinding.tvNotes.setText(r.notes);
            mBinding.tvNotes.setVisibility(View.VISIBLE);
        } else {
            mBinding.tvNotes.setVisibility(View.GONE);
        }
    }

    private void bindMoreDetails(RaceResultEntity r) {
        // Location
        StringBuilder loc = new StringBuilder(getString(R.string.detail_location)).append(":  ");
        boolean hasLocation = r.raceCity != null || r.raceState != null || r.raceCountry != null;
        if (hasLocation) {
            if (r.raceCity    != null) loc.append(r.raceCity);
            if (r.raceState   != null) { if (r.raceCity != null) loc.append(", "); loc.append(r.raceState); }
            if (r.raceCountry != null) { if (r.raceCity != null || r.raceState != null) loc.append(", "); loc.append(r.raceCountry); }
        } else {
            loc.append("—");
        }
        mBinding.tvLocationRow.setText(loc.toString());

        // Bib
        mBinding.tvBibRow.setText(getString(R.string.detail_bib) + ":  "
            + (r.bibNumber != null ? r.bibNumber : "—"));

        // Surface
        mBinding.tvSurfaceRow.setText(getString(R.string.detail_surface) + ":  "
            + (r.surfaceType != null ? capitalize(r.surfaceType) : "—"));

        // Elevation
        StringBuilder elev = new StringBuilder(getString(R.string.detail_elevation)).append(":  ");
        if (r.elevationGainMeters != null) {
            elev.append(r.elevationGainMeters).append("m gain");
            if (r.elevationStartMeters != null) {
                elev.append("  ·  ").append(r.elevationStartMeters).append("m start");
            }
        } else if (r.elevationStartMeters != null) {
            elev.append(r.elevationStartMeters).append("m start elevation");
        } else {
            elev.append("—");
        }
        mBinding.tvElevationRow.setText(elev.toString());

        // Weather
        StringBuilder weather = new StringBuilder(getString(R.string.detail_weather)).append(":  ");
        if (r.temperatureCelsius != null) {
            weather.append(Math.round(r.temperatureCelsius)).append("°C");
            if (r.weatherCondition != null) weather.append("  ·  ").append(r.weatherCondition);
        } else if (r.weatherCondition != null) {
            weather.append(r.weatherCondition);
        } else {
            weather.append("—");
        }
        mBinding.tvWeatherRow.setText(weather.toString());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.detail_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_edit_result) {
            Bundle args = new Bundle();
            args.putString("resultId", mResultId);
            Navigation.findNavController(requireView())
                .navigate(R.id.action_detail_to_edit, args);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return "";
        try {
            String[] parts = isoDate.split("-");
            String[] months = {"","Jan","Feb","Mar","Apr","May","Jun",
                               "Jul","Aug","Sep","Oct","Nov","Dec"};
            int m = Integer.parseInt(parts[1]);
            return months[m] + " " + Integer.parseInt(parts[2]) + ", " + parts[0];
        } catch (Exception e) { return isoDate; }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
