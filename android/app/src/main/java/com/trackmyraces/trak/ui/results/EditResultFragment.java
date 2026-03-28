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
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.dto.EnrichResponse;
import com.trackmyraces.trak.databinding.FragmentEditResultBinding;
import com.trackmyraces.trak.ui.NetworkAwareFragment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * EditResultFragment — edit all editable fields on a claimed result.
 *
 * Sections: Re-populate, Timing, Distance, Placement, Location,
 *           Race details, Conditions, Notes.
 *
 * Unit handling:
 *   - Temperature stored as °C; displayed/entered in user's preferred unit (°F or °C).
 *   - Elevation stored as meters; displayed/entered in user's preferred unit (ft or m).
 *   - Distance stored as meters; canonical label displayed in user's preferred unit.
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

    // Cached profile for unit conversion and gender/age auto-fill
    private RunnerProfileEntity mProfile;

    // Whether the currently displayed distance is estimated (from enrich)
    private boolean mDistanceIsEstimated = false;

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

        mViewModel.profile.observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) applyProfile(profile);
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

        // Observe map picker result (returned via savedStateHandle when user confirms a pin)
        Navigation.findNavController(view)
            .getCurrentBackStackEntry()
            .getSavedStateHandle()
            .<Double>getLiveData(MapPickerFragment.RESULT_LAT)
            .observe(getViewLifecycleOwner(), lat -> {
                if (lat == null || lat == 0) return;
                Double lon     = Navigation.findNavController(view)
                    .getCurrentBackStackEntry()
                    .getSavedStateHandle()
                    .get(MapPickerFragment.RESULT_LON);
                String city    = Navigation.findNavController(view)
                    .getCurrentBackStackEntry()
                    .getSavedStateHandle()
                    .get(MapPickerFragment.RESULT_CITY);
                String state   = Navigation.findNavController(view)
                    .getCurrentBackStackEntry()
                    .getSavedStateHandle()
                    .get(MapPickerFragment.RESULT_STATE);
                String country = Navigation.findNavController(view)
                    .getCurrentBackStackEntry()
                    .getSavedStateHandle()
                    .get(MapPickerFragment.RESULT_COUNTRY);

                if (lon == null) return;

                // Fill location fields with what the map returned
                setText(mBinding.etCity,    city);
                setText(mBinding.etState,   state);
                setText(mBinding.etCountry, country);

                // Fetch elevation + weather for the exact pin coordinates
                RaceResultEntity r = mViewModel.result.getValue();
                if (r != null) {
                    r.raceCity    = city;
                    r.raceState   = state;
                    r.raceCountry = country;
                    mViewModel.enrichAtCoords(lat, lon, r);
                }

                // Clear so it doesn't re-fire on rotation
                Navigation.findNavController(view)
                    .getCurrentBackStackEntry()
                    .getSavedStateHandle()
                    .set(MapPickerFragment.RESULT_LAT, 0.0);
            });

        mViewModel.getEnriching().observe(getViewLifecycleOwner(), enriching -> {
            mBinding.btnRepopulate.setEnabled(!Boolean.TRUE.equals(enriching));
            mBinding.btnRepopulate.setText(Boolean.TRUE.equals(enriching)
                ? getString(R.string.loading)
                : getString(R.string.edit_set_location_button));
        });

        // "Set race location" — open geocoding search; on pick, fills location + fetches elevation/weather
        mBinding.btnRepopulate.setOnClickListener(v -> showLocationSearch());

        // Clear estimated flag when user manually changes the canonical dropdown
        mBinding.etDistanceCanonical.setOnItemClickListener((parent, v2, pos, id) ->
            setDistanceEstimated(false));

        mBinding.btnSave.setOnClickListener(v2 -> saveForm());
    }

    // ── Profile-driven auto-fill ──────────────────────────────────────────

    private void applyProfile(RunnerProfileEntity profile) {
        mProfile = profile;

        // Pre-fill gender from profile if the field is blank
        if (str(mBinding.etGender) == null && profile.gender != null) {
            setDropdown(mBinding.etGender, profile.gender);
        }

        // Apply unit suffixes
        applySuffixes(profile);

        // Recalculate age-at-race label if result is already loaded
        RaceResultEntity result = mViewModel.result.getValue();
        if (result != null) {
            showAgeAtRace(profile, result.raceDate);
        }
    }

    private void applySuffixes(RunnerProfileEntity profile) {
        boolean imperial    = "imperial".equalsIgnoreCase(profile.preferredUnits);
        boolean fahrenheit  = "fahrenheit".equalsIgnoreCase(profile.preferredTempUnit);
        mBinding.tilTemperature.setSuffixText(fahrenheit ? "°F" : "°C");
        mBinding.tilElevationGain.setSuffixText(imperial ? "ft" : "m");
        mBinding.tilElevationStart.setSuffixText(imperial ? "ft" : "m");
    }

    private void showAgeAtRace(RunnerProfileEntity profile, String raceDate) {
        if (profile.dateOfBirth == null || raceDate == null) {
            mBinding.tvAgeAtRace.setVisibility(View.GONE);
            return;
        }
        int age = calcAgeAtRace(profile.dateOfBirth, raceDate);
        if (age > 0) {
            mBinding.tvAgeAtRace.setText(getString(R.string.edit_hint_age_at_race, age));
            mBinding.tvAgeAtRace.setVisibility(View.VISIBLE);
        } else {
            mBinding.tvAgeAtRace.setVisibility(View.GONE);
        }
    }

    // ── Form population ───────────────────────────────────────────────────

    private void populateForm(RaceResultEntity r) {
        // Timing
        setText(mBinding.etFinishTime, r.finishTime);
        setText(mBinding.etChipTime,   r.chipTime);

        // Distance
        setText(mBinding.etDistanceLabel, r.distanceLabel);
        setDropdown(mBinding.etDistanceCanonical, canonicalToLabel(r.distanceCanonical));
        mDistanceIsEstimated = r.isDistanceEstimated;
        updateEstimatedHelperText();

        // Placement
        setDropdown(mBinding.etGender, r.gender);
        setText(mBinding.etAgeGroup,   r.ageGroupLabel);

        // Location
        setText(mBinding.etCity,    r.raceCity);
        setText(mBinding.etState,   r.raceState);
        setText(mBinding.etCountry, r.raceCountry);

        // Race details
        setText(mBinding.etBib,      r.bibNumber);
        setDropdown(mBinding.etSurface, r.surfaceType);

        // Conditions — convert stored metric values to user's preferred unit
        boolean imperial   = mProfile != null && "imperial".equalsIgnoreCase(mProfile.preferredUnits);
        boolean fahrenheit = mProfile != null && "fahrenheit".equalsIgnoreCase(mProfile.preferredTempUnit);

        if (r.elevationGainMeters != null) {
            double gain = imperial ? r.elevationGainMeters * 3.28084 : r.elevationGainMeters;
            setText(mBinding.etElevationGain, String.valueOf(imperial ? (int) Math.round(gain) : r.elevationGainMeters));
        }
        if (r.elevationStartMeters != null) {
            double start = imperial ? r.elevationStartMeters * 3.28084 : r.elevationStartMeters;
            setText(mBinding.etElevationStart, String.valueOf(imperial ? (int) Math.round(start) : r.elevationStartMeters));
        }
        if (r.temperatureCelsius != null) {
            double temp = fahrenheit ? r.temperatureCelsius * 9.0 / 5.0 + 32 : r.temperatureCelsius;
            setText(mBinding.etTemperature, String.format(Locale.US, "%.1f", temp));
        }

        setText(mBinding.etWeather, r.weatherCondition);

        // Notes
        setText(mBinding.etNotes, r.notes);

        // Age at race
        if (mProfile != null) showAgeAtRace(mProfile, r.raceDate);
    }

    // ── Location search ───────────────────────────────────────────────────

    private void showLocationSearch() {
        Navigation.findNavController(requireView())
            .navigate(R.id.action_edit_to_map_picker);
    }

    // ── Enrich response ───────────────────────────────────────────────────

    private void applyEnrichResponse(EnrichResponse resp) {
        if (resp == null) return;

        // Only fill blank fields — never overwrite user-entered data
        if (resp.distanceLabel != null && str(mBinding.etDistanceLabel) == null) {
            setText(mBinding.etDistanceLabel, resp.distanceLabel);
        }
        if (resp.distanceCanonical != null && str(mBinding.etDistanceCanonical) == null) {
            setDropdown(mBinding.etDistanceCanonical, canonicalToLabel(resp.distanceCanonical));
            if (Boolean.TRUE.equals(resp.distanceIsEstimated)) {
                setDistanceEstimated(true);
            }
        }
        // Fill resolved location from geocoding if fields are still blank
        if (resp.resolvedCity != null && str(mBinding.etCity) == null) {
            setText(mBinding.etCity, resp.resolvedCity);
        }
        if (resp.resolvedState != null && str(mBinding.etState) == null) {
            setText(mBinding.etState, resp.resolvedState);
        }
        if (resp.resolvedCountry != null && str(mBinding.etCountry) == null) {
            setText(mBinding.etCountry, resp.resolvedCountry);
        }
        if (resp.elevationStartMeters != null && str(mBinding.etElevationStart) == null) {
            boolean imperial = mProfile != null && "imperial".equalsIgnoreCase(mProfile.preferredUnits);
            double val = imperial ? resp.elevationStartMeters * 3.28084 : resp.elevationStartMeters;
            setText(mBinding.etElevationStart, String.valueOf(imperial ? (int) Math.round(val) : resp.elevationStartMeters));
        }
        if (resp.temperatureCelsius != null && str(mBinding.etTemperature) == null) {
            boolean fahrenheit = mProfile != null && "fahrenheit".equalsIgnoreCase(mProfile.preferredTempUnit);
            double temp = fahrenheit ? resp.temperatureCelsius * 9.0 / 5.0 + 32 : resp.temperatureCelsius;
            setText(mBinding.etTemperature, String.format(Locale.US, "%.1f", temp));
        }
        if (resp.weatherCondition != null && str(mBinding.etWeather) == null) {
            setText(mBinding.etWeather, resp.weatherCondition);
        }

        Toast.makeText(requireContext(), R.string.edit_repopulate_done, Toast.LENGTH_SHORT).show();
    }

    private void setDistanceEstimated(boolean estimated) {
        mDistanceIsEstimated = estimated;
        updateEstimatedHelperText();
    }

    private void updateEstimatedHelperText() {
        mBinding.tilDistanceCanonical.setHelperText(
            mDistanceIsEstimated ? getString(R.string.edit_distance_estimated) : null);
    }

    // ── Save ──────────────────────────────────────────────────────────────

    private void saveForm() {
        RaceResultEntity current = mViewModel.result.getValue();
        if (current == null) return;

        boolean imperial   = mProfile != null && "imperial".equalsIgnoreCase(mProfile.preferredUnits);
        boolean fahrenheit = mProfile != null && "fahrenheit".equalsIgnoreCase(mProfile.preferredTempUnit);

        // Timing
        current.finishTime = str(mBinding.etFinishTime);
        current.chipTime   = str(mBinding.etChipTime);

        // Distance
        current.distanceLabel     = str(mBinding.etDistanceLabel);
        current.distanceCanonical = labelToCanonical(str(mBinding.etDistanceCanonical));
        current.isDistanceEstimated = mDistanceIsEstimated;

        // Placement
        current.gender        = str(mBinding.etGender);
        current.ageGroupLabel = str(mBinding.etAgeGroup);

        // Auto-calculate age at race from profile DOB
        if (mProfile != null && mProfile.dateOfBirth != null && current.raceDate != null) {
            int age = calcAgeAtRace(mProfile.dateOfBirth, current.raceDate);
            if (age > 0) current.ageAtRace = age;
        }

        // Location
        current.raceCity    = str(mBinding.etCity);
        current.raceState   = str(mBinding.etState);
        current.raceCountry = str(mBinding.etCountry);

        // Race details
        current.bibNumber   = str(mBinding.etBib);
        current.surfaceType = str(mBinding.etSurface);

        // Conditions — convert display units back to metric for storage
        String gainStr = str(mBinding.etElevationGain);
        if (gainStr != null) {
            Integer gainDisplay = parseInt(gainStr);
            if (gainDisplay != null) {
                current.elevationGainMeters = imperial ? (int) Math.round(gainDisplay / 3.28084) : gainDisplay;
            }
        } else {
            current.elevationGainMeters = null;
        }

        String elevStartStr = str(mBinding.etElevationStart);
        if (elevStartStr != null) {
            Integer elevDisplay = parseInt(elevStartStr);
            if (elevDisplay != null) {
                current.elevationStartMeters = imperial ? (int) Math.round(elevDisplay / 3.28084) : elevDisplay;
            }
        } else {
            current.elevationStartMeters = null;
        }

        String tempStr = str(mBinding.etTemperature);
        if (tempStr != null) {
            Double tempDisplay = parseDouble(tempStr);
            if (tempDisplay != null) {
                current.temperatureCelsius = fahrenheit ? (tempDisplay - 32) * 5.0 / 9.0 : tempDisplay;
            }
        } else {
            current.temperatureCelsius = null;
        }

        current.weatherCondition = str(mBinding.etWeather);
        current.notes            = str(mBinding.etNotes);

        current.isSynced  = false;
        current.updatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .format(new Date());

        mViewModel.save(current);
    }

    // ── Dropdowns ─────────────────────────────────────────────────────────

    private void setupDropdowns() {
        mBinding.etSurface.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, SURFACE_OPTIONS));

        mBinding.etDistanceCanonical.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, DISTANCE_OPTIONS));

        mBinding.etGender.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_dropdown_item_1line, GENDER_OPTIONS));
    }

    // ── Distance label ↔ canonical mapping ────────────────────────────────

    private String canonicalToLabel(String canonical) {
        if (canonical == null) return null;
        for (int i = 0; i < DISTANCE_CANONICAL.length; i++) {
            if (DISTANCE_CANONICAL[i].equalsIgnoreCase(canonical)) return DISTANCE_OPTIONS[i];
        }
        return canonical;
    }

    private String labelToCanonical(String label) {
        if (label == null) return null;
        for (int i = 0; i < DISTANCE_OPTIONS.length; i++) {
            if (DISTANCE_OPTIONS[i].equalsIgnoreCase(label)) return DISTANCE_CANONICAL[i];
        }
        return label.toLowerCase(Locale.US).replaceAll("\\s+", "");
    }

    // ── Age calculation ────────────────────────────────────────────────────

    /** Returns age in years at the time of the race, or -1 on parse error. */
    private int calcAgeAtRace(String dob, String raceDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date birth = sdf.parse(dob);
            Date race  = sdf.parse(raceDate);
            if (birth == null || race == null) return -1;

            Calendar birthCal = Calendar.getInstance();
            birthCal.setTime(birth);
            Calendar raceCal = Calendar.getInstance();
            raceCal.setTime(race);

            int age = raceCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR);
            // Subtract one if birthday hasn't occurred yet in the race year
            birthCal.set(Calendar.YEAR, raceCal.get(Calendar.YEAR));
            if (raceCal.before(birthCal)) age--;
            return age;
        } catch (ParseException e) {
            return -1;
        }
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
