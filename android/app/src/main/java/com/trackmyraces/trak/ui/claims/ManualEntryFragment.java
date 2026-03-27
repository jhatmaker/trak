package com.trackmyraces.trak.ui.claims;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.trackmyraces.trak.ui.NetworkAwareFragment;
import androidx.navigation.Navigation;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.databinding.FragmentManualEntryBinding;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * ManualEntryFragment
 *
 * Allows the runner to enter a race result by hand — no network required.
 * Saves directly to Room with isSynced=false so SyncManager can upload later.
 *
 * Required fields: race name, race date, distance, finish time.
 * Optional: bib number, notes.
 */
public class ManualEntryFragment extends NetworkAwareFragment {

    // Canonical distance entries — label shown to user : canonical key : meters
    private static final String[] DISTANCE_LABELS = {
        "1 Mile", "5K", "8K", "10K", "15K", "10 Mile",
        "Half Marathon", "25K", "30K", "Marathon",
        "50K", "50 Mile", "100K", "100 Mile", "Other"
    };
    private static final String[] DISTANCE_KEYS = {
        "1mile", "5k", "8k", "10k", "15k", "10mile",
        "halfmarathon", "25k", "30k", "marathon",
        "50k", "50mile", "100k", "100mile", "other"
    };
    private static final double[] DISTANCE_METERS = {
        1609, 5000, 8047, 10000, 15000, 16093,
        21097, 25000, 30000, 42195,
        50000, 80467, 100000, 160934, 0
    };

    private FragmentManualEntryBinding mBinding;
    private RaceResultRepository       mRepo;
    private String                     mSelectedDate; // YYYY-MM-DD

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentManualEntryBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRepo = new RaceResultRepository(requireActivity().getApplication());

        setupDistanceDropdown();
        setupDatePicker();
        mBinding.btnSave.setOnClickListener(v -> attemptSave());
    }

    private void setupDistanceDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            DISTANCE_LABELS
        );
        mBinding.acvDistance.setAdapter(adapter);
    }

    private void setupDatePicker() {
        Runnable openPicker = () -> {
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

            // Default selection: today in UTC
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.manual_race_date_hint))
                .setSelection(utc.getTimeInMillis())
                .setCalendarConstraints(constraints)
                .build();

            picker.addOnPositiveButtonClickListener(selectionMs -> {
                Calendar result = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                result.setTimeInMillis(selectionMs);
                mSelectedDate = String.format(Locale.US, "%04d-%02d-%02d",
                    result.get(Calendar.YEAR),
                    result.get(Calendar.MONTH) + 1,
                    result.get(Calendar.DAY_OF_MONTH));
                mBinding.etRaceDate.setText(mSelectedDate);
            });

            picker.show(getParentFragmentManager(), "race_date_picker");
        };

        mBinding.etRaceDate.setOnClickListener(v -> openPicker.run());
        mBinding.tilRaceDate.setEndIconOnClickListener(v -> openPicker.run());
    }

    private void attemptSave() {
        // Clear previous error
        mBinding.tvError.setVisibility(View.GONE);
        mBinding.tilRaceName.setError(null);
        mBinding.tilRaceDate.setError(null);
        mBinding.tilDistance.setError(null);
        mBinding.tilFinishTime.setError(null);

        String raceName   = getText(mBinding.etRaceName);
        String finishTime = getText(mBinding.etFinishTime);
        String distLabel  = getText(mBinding.acvDistance);
        String bib        = getText(mBinding.etBib);
        String notes      = getText(mBinding.etNotes);

        // Validate required fields
        boolean valid = true;
        if (raceName.isEmpty()) {
            mBinding.tilRaceName.setError(getString(R.string.manual_error_required));
            valid = false;
        }
        if (mSelectedDate == null) {
            mBinding.tilRaceDate.setError(getString(R.string.manual_error_required));
            valid = false;
        }
        if (distLabel.isEmpty()) {
            mBinding.tilDistance.setError(getString(R.string.manual_error_required));
            valid = false;
        }
        if (finishTime.isEmpty()) {
            mBinding.tilFinishTime.setError(getString(R.string.manual_error_required));
            valid = false;
        }
        if (!valid) return;

        // Parse finish time to seconds
        int finishSeconds = parseTimeToSeconds(finishTime);
        if (finishSeconds <= 0) {
            mBinding.tilFinishTime.setError(getString(R.string.manual_error_time_format));
            return;
        }

        // Resolve canonical distance
        int distIndex = Arrays.asList(DISTANCE_LABELS).indexOf(distLabel);
        String distCanonical = (distIndex >= 0) ? DISTANCE_KEYS[distIndex]   : "other";
        double distMeters    = (distIndex >= 0) ? DISTANCE_METERS[distIndex] : 0;

        // Build entity
        RaceResultEntity entity = new RaceResultEntity();
        entity.id               = UUID.randomUUID().toString();
        entity.raceName         = raceName;
        entity.raceNameCanonical = canonicaliseName(raceName);
        entity.raceDate         = mSelectedDate;
        entity.distanceLabel    = distLabel;
        entity.distanceCanonical = distCanonical;
        entity.distanceMeters   = distMeters;
        entity.finishTime       = finishTime;
        entity.finishSeconds    = finishSeconds;
        entity.bibNumber        = bib.isEmpty() ? null : bib;
        entity.notes            = notes.isEmpty() ? null : notes;
        entity.status           = "active";
        entity.isSynced         = false;   // will be synced when online
        entity.recordedAt       = nowIso();
        entity.updatedAt        = entity.recordedAt;

        // Compute simple pace (seconds per km)
        if (distMeters > 0) {
            entity.pacePerKmSeconds = (int) Math.round(finishSeconds / (distMeters / 1000.0));
        }

        mBinding.btnSave.setEnabled(false);

        mRepo.saveManualResult(entity, new RaceResultRepository.RepositoryCallback<String>() {
            @Override
            public void onSuccess(String resultId) {
                requireActivity().runOnUiThread(() -> {
                    Bundle args = new Bundle();
                    args.putString("resultId", resultId);
                    Navigation.findNavController(requireView())
                        .navigate(R.id.action_manual_to_detail, args);
                });
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    mBinding.btnSave.setEnabled(true);
                    mBinding.tvError.setText(getString(R.string.manual_error_save));
                    mBinding.tvError.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    /** Parse H:MM:SS or MM:SS → total seconds. Returns 0 on parse failure. */
    private int parseTimeToSeconds(String time) {
        try {
            String[] parts = time.trim().split(":");
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                     + Integer.parseInt(parts[1]) * 60
                     + Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60
                     + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    /** Minimal race name slug for deduplication (matches backend logic). */
    private String canonicaliseName(String raw) {
        return raw.toLowerCase(Locale.US)
            .replaceAll("\\b20\\d{2}\\b", "")
            .replaceAll("[^a-z0-9 ]", "")
            .replaceAll("\\s+", " ")
            .trim()
            .replace(" ", "-");
    }

    private String getText(android.widget.TextView tv) {
        CharSequence t = tv.getText();
        return t != null ? t.toString().trim() : "";
    }

    private String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new java.util.Date());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
