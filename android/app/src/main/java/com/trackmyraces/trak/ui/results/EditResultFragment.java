package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.network.dto.EnrichResponse;
import com.trackmyraces.trak.databinding.FragmentEditResultBinding;
import com.trackmyraces.trak.ui.NetworkAwareFragment;

/**
 * EditResultFragment — edit all editable fields on a claimed result.
 *
 * Sections: Re-populate, Timing, Distance, Placement, Location,
 *           Race details, Conditions, Notes.
 *
 * Computed/PR fields are not editable here.
 */
public class EditResultFragment extends NetworkAwareFragment {

    private static final String[] SURFACE_OPTIONS = {
        "road", "trail", "track", "xc", "mixed"
    };

    private static final String[] DISTANCE_OPTIONS = {
        "1 mile", "5K", "8K", "10K", "15K", "10 mile",
        "Half marathon", "25K", "30K", "Marathon",
        "50K", "50 mile", "100K", "100 mile"
    };

    private static final String[] DISTANCE_CANONICAL = {
        "1mile", "5k", "8k", "10k", "15k", "10mile",
        "halfmarathon", "25k", "30k", "marathon",
        "50k", "50mile", "100k", "100mile"
    };

    private static final String[] GENDER_OPTIONS = {
        "M", "F", "NB", "unknown"
    };

    private FragmentEditResultBinding mBinding;
    private EditResultViewModel       mViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentEditResultBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String resultId = null;
        if (getArguments() != null) resultId = getArguments().getString("resultId");

        mViewModel = new ViewModelProvider(this,
            new EditResultViewModel.Factory(requireActivity().getApplication(), resultId))
            .get(EditResultViewModel.class);

        setupDropdowns();

        mViewModel.result.observe(getViewLifecycleOwner(), result -> {
            if (result != null) populateForm(result);
        });

        mViewModel.getSaved().observe(getViewLifecycleOwner(), saved -> {
            if (Boolean.TRUE.equals(saved)) {
                Navigation.findNavController(view).popBackStack();
            }
        });

        mViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        mViewModel.getEnriched().observe(getViewLifecycleOwner(), this::applyEnrichResponse);

        mViewModel.getEnriching().observe(getViewLifecycleOwner(), enriching -> {
            mBinding.btnRepopulate.setEnabled(!Boolean.TRUE.equals(enriching));
            mBinding.btnRepopulate.setText(Boolean.TRUE.equals(enriching)
                ? getString(R.string.loading)
                : getString(R.string.edit_repopulate_button));
        });

        mBinding.btnRepopulate.setOnClickListener(v -> {
            RaceResultEntity r = mViewModel.result.getValue();
            if (r != null) {
                // Use any edits already in the form for city/state/country
                r.raceCity    = str(mBinding.etCity);
                r.raceState   = str(mBinding.etState);
                r.raceCountry = str(mBinding.etCountry);
                mViewModel.enrich(r);
            }
        });

        mBinding.btnSave.setOnClickListener(v -> saveForm());
    }

    private void setupDropdowns() {
        ArrayAdapter<String> surfaceAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            SURFACE_OPTIONS
        );
        mBinding.etSurface.setAdapter(surfaceAdapter);

        ArrayAdapter<String> distanceAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            DISTANCE_OPTIONS
        );
        mBinding.etDistanceCanonical.setAdapter(distanceAdapter);

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            GENDER_OPTIONS
        );
        mBinding.etGender.setAdapter(genderAdapter);
    }

    private void populateForm(RaceResultEntity r) {
        // Timing
        setText(mBinding.etFinishTime,    r.finishTime);
        setText(mBinding.etChipTime,      r.chipTime);

        // Distance
        setText(mBinding.etDistanceLabel, r.distanceLabel);
        setDropdown(mBinding.etDistanceCanonical, canonicalToLabel(r.distanceCanonical));

        // Placement
        setDropdown(mBinding.etGender, r.gender);
        setText(mBinding.etAgeGroup,       r.ageGroupLabel);

        // Location
        setText(mBinding.etCity,           r.raceCity);
        setText(mBinding.etState,          r.raceState);
        setText(mBinding.etCountry,        r.raceCountry);

        // Race details
        setText(mBinding.etBib,            r.bibNumber);
        setDropdown(mBinding.etSurface,    r.surfaceType);

        // Conditions
        setText(mBinding.etElevationGain,  r.elevationGainMeters != null
            ? String.valueOf(r.elevationGainMeters) : null);
        setText(mBinding.etElevationStart, r.elevationStartMeters != null
            ? String.valueOf(r.elevationStartMeters) : null);
        setText(mBinding.etTemperature,    r.temperatureCelsius != null
            ? String.valueOf(r.temperatureCelsius) : null);
        setText(mBinding.etWeather,        r.weatherCondition);

        // Notes
        setText(mBinding.etNotes,          r.notes);
    }

    /** Apply enriched values from /enrich — only overwrite blank or explicitly provided fields. */
    private void applyEnrichResponse(EnrichResponse resp) {
        if (resp == null) return;

        if (resp.distanceLabel != null && str(mBinding.etDistanceLabel) == null) {
            setText(mBinding.etDistanceLabel, resp.distanceLabel);
        }
        if (resp.distanceCanonical != null) {
            String label = canonicalToLabel(resp.distanceCanonical);
            if (label != null && str(mBinding.etDistanceCanonical) == null) {
                setDropdown(mBinding.etDistanceCanonical, label);
            }
        }
        if (resp.elevationStartMeters != null && str(mBinding.etElevationStart) == null) {
            setText(mBinding.etElevationStart, String.valueOf(resp.elevationStartMeters));
        }
        if (resp.temperatureCelsius != null && str(mBinding.etTemperature) == null) {
            setText(mBinding.etTemperature, String.valueOf(resp.temperatureCelsius));
        }
        if (resp.weatherCondition != null && str(mBinding.etWeather) == null) {
            setText(mBinding.etWeather, resp.weatherCondition);
        }

        Toast.makeText(requireContext(), R.string.edit_repopulate_done, Toast.LENGTH_SHORT).show();
    }

    private void saveForm() {
        RaceResultEntity current = mViewModel.result.getValue();
        if (current == null) return;

        // Timing
        current.finishTime = str(mBinding.etFinishTime);
        current.chipTime   = str(mBinding.etChipTime);

        // Distance
        current.distanceLabel     = str(mBinding.etDistanceLabel);
        String canonicalLabel     = str(mBinding.etDistanceCanonical);
        current.distanceCanonical = labelToCanonical(canonicalLabel);

        // Placement
        current.gender       = str(mBinding.etGender);
        current.ageGroupLabel = str(mBinding.etAgeGroup);

        // Location
        current.raceCity    = str(mBinding.etCity);
        current.raceState   = str(mBinding.etState);
        current.raceCountry = str(mBinding.etCountry);

        // Race details
        current.bibNumber   = str(mBinding.etBib);
        current.surfaceType = str(mBinding.etSurface);

        // Conditions
        String gainStr = str(mBinding.etElevationGain);
        current.elevationGainMeters = gainStr != null ? parseInt(gainStr) : null;

        String elevStartStr = str(mBinding.etElevationStart);
        current.elevationStartMeters = elevStartStr != null ? parseInt(elevStartStr) : null;

        String tempStr = str(mBinding.etTemperature);
        current.temperatureCelsius = tempStr != null ? parseDouble(tempStr) : null;

        current.weatherCondition = str(mBinding.etWeather);

        // Notes
        current.notes = str(mBinding.etNotes);

        current.isSynced  = false;
        current.updatedAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.US).format(new java.util.Date());

        mViewModel.save(current);
    }

    // ── Distance label ↔ canonical mapping ────────────────────────────────

    private String canonicalToLabel(String canonical) {
        if (canonical == null) return null;
        for (int i = 0; i < DISTANCE_CANONICAL.length; i++) {
            if (DISTANCE_CANONICAL[i].equalsIgnoreCase(canonical)) return DISTANCE_OPTIONS[i];
        }
        return canonical; // unknown canonical — show as-is
    }

    private String labelToCanonical(String label) {
        if (label == null) return null;
        for (int i = 0; i < DISTANCE_OPTIONS.length; i++) {
            if (DISTANCE_OPTIONS[i].equalsIgnoreCase(label)) return DISTANCE_CANONICAL[i];
        }
        return label.toLowerCase().replaceAll("\\s+", ""); // fallback normalise
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void setText(android.widget.EditText view, String value) {
        if (view != null && value != null) view.setText(value);
    }

    private void setDropdown(AutoCompleteTextView view, String value) {
        if (view != null && value != null) view.setText(value, false);
    }

    private String str(android.widget.EditText view) {
        if (view == null) return null;
        String s = view.getText() != null ? view.getText().toString().trim() : "";
        return s.isEmpty() ? null : s;
    }

    private Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
