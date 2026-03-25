package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
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
public class ResultDetailFragment extends Fragment {

    private FragmentResultDetailBinding mBinding;
    private ResultDetailViewModel       mViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentResultDetailBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get resultId from navigation args
        String resultId = null;
        if (getArguments() != null) resultId = getArguments().getString("resultId");

        mViewModel = new ViewModelProvider(this,
            new ResultDetailViewModel.Factory(requireActivity().getApplication(), resultId))
            .get(ResultDetailViewModel.class);

        observeViewModel();
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

        // Placement row
        mBinding.tvOverall.setText(r.overallPlace != null && r.overallTotal != null
            ? getString(R.string.place_of, r.overallPlace, r.overallTotal) : "—");

        mBinding.tvGenderPlace.setText(r.genderPlace != null && r.genderTotal != null
            ? getString(R.string.place_of, r.genderPlace, r.genderTotal) : "—");

        mBinding.tvAgPlace.setText(r.ageGroupPlace != null && r.ageGroupTotal != null
            ? getString(R.string.place_of, r.ageGroupPlace, r.ageGroupTotal) : "—");

        mBinding.tvAgLabel.setText(r.ageGroupLabel != null ? r.ageGroupLabel
            : r.ageGroupCalc != null ? r.ageGroupCalc : getString(R.string.detail_age_group));

        // Detail rows
        mBinding.tvChipTimeRow.setText(
            getString(R.string.detail_chip_time) + ":  "
            + (r.chipTime != null ? r.chipTime : "—"));

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

        // Notes
        if (r.notes != null && !r.notes.isEmpty()) {
            mBinding.tvNotes.setText(r.notes);
            mBinding.tvNotes.setVisibility(View.VISIBLE);
        }

        // Source URL
        if (r.sourceUrl != null) {
            mBinding.tvSource.setText(r.sourceUrl);
            mBinding.tvSource.setVisibility(View.VISIBLE);
        }
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
