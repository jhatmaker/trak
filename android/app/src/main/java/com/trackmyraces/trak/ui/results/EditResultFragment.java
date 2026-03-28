package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.databinding.FragmentEditResultBinding;
import com.trackmyraces.trak.ui.NetworkAwareFragment;

/**
 * EditResultFragment — edit supplementary fields on a claimed result.
 *
 * Editable: location (city, state, country), bib number, surface type,
 * elevation gain (m), temperature (°C), weather condition, notes.
 *
 * Timing, placement, and computed fields (PR, BQ, age grade) are read-only
 * and not shown here — they reflect the original race data.
 */
public class EditResultFragment extends NetworkAwareFragment {

    private static final String[] SURFACE_OPTIONS = {
        "road", "trail", "track", "xc", "mixed"
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

        setupSurfaceDropdown();

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

        mBinding.btnSave.setOnClickListener(v -> saveForm());
    }

    private void setupSurfaceDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            SURFACE_OPTIONS
        );
        mBinding.etSurface.setAdapter(adapter);
    }

    private void populateForm(RaceResultEntity r) {
        setText(mBinding.etCity,           r.raceCity);
        setText(mBinding.etState,          r.raceState);
        setText(mBinding.etCountry,        r.raceCountry);
        setText(mBinding.etBib,            r.bibNumber);
        setText(mBinding.etSurface,        r.surfaceType);
        setText(mBinding.etElevationGain,  r.elevationGainMeters != null
            ? String.valueOf(r.elevationGainMeters) : null);
        setText(mBinding.etTemperature,    r.temperatureCelsius != null
            ? String.valueOf(r.temperatureCelsius) : null);
        setText(mBinding.etWeather,        r.weatherCondition);
        setText(mBinding.etNotes,          r.notes);
    }

    private void saveForm() {
        RaceResultEntity current = mViewModel.result.getValue();
        if (current == null) return;

        current.raceCity          = str(mBinding.etCity);
        current.raceState         = str(mBinding.etState);
        current.raceCountry       = str(mBinding.etCountry);
        current.bibNumber         = str(mBinding.etBib);
        current.surfaceType       = str(mBinding.etSurface);
        current.weatherCondition  = str(mBinding.etWeather);
        current.notes             = str(mBinding.etNotes);

        String gainStr = str(mBinding.etElevationGain);
        current.elevationGainMeters = gainStr != null ? parseInt(gainStr) : null;

        String tempStr = str(mBinding.etTemperature);
        current.temperatureCelsius = tempStr != null ? parseDouble(tempStr) : null;

        current.isSynced  = false;
        current.updatedAt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.US).format(new java.util.Date());

        mViewModel.save(current);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void setText(android.widget.EditText view, String value) {
        if (view != null && value != null) view.setText(value);
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
