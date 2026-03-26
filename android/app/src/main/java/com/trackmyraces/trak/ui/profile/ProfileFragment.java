package com.trackmyraces.trak.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.databinding.FragmentProfileBinding;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ProfileFragment — edit runner profile and manage linked credential sites.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding mBinding;
    private ProfileViewModel       mViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentProfileBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        setupDatePicker();
        setupSaveButton();
        observeViewModel();
    }

    private void setupDatePicker() {
        View.OnClickListener openPicker = v -> {
            // Resolve initial selection: parse existing DOB or default to 30 years ago
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            String existing = getText(mBinding.etDob);
            if (existing.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] p = existing.split("-");
                utc.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1,
                        Integer.parseInt(p[2]), 0, 0, 0);
                utc.set(Calendar.MILLISECOND, 0);
            } else {
                utc.add(Calendar.YEAR, -30);
            }

            // Constrain to past dates only
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Date of birth")
                .setSelection(utc.getTimeInMillis())
                .setCalendarConstraints(constraints)
                .build();

            picker.addOnPositiveButtonClickListener(selectionMs -> {
                Calendar result = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                result.setTimeInMillis(selectionMs);
                String dob = String.format(Locale.US, "%04d-%02d-%02d",
                    result.get(Calendar.YEAR),
                    result.get(Calendar.MONTH) + 1,
                    result.get(Calendar.DAY_OF_MONTH));
                mBinding.etDob.setText(dob);
                mBinding.tilDob.setError(null);
            });

            picker.show(getParentFragmentManager(), "dob_picker");
        };

        mBinding.etDob.setOnClickListener(openPicker);
        mBinding.tilDob.setEndIconOnClickListener(openPicker);
    }

    private void setupSaveButton() {
        mBinding.btnSaveProfile.setOnClickListener(v -> {
            String name = getText(mBinding.etName);
            String dob  = getText(mBinding.etDob);

            if (name.isEmpty()) {
                mBinding.tilName.setError(getString(R.string.profile_name_hint) + " is required");
                return;
            }
            if (dob.isEmpty() || !dob.matches("\\d{4}-\\d{2}-\\d{2}")) {
                mBinding.tilDob.setError("Please select a date of birth");
                return;
            }

            String gender = "prefer_not_to_say";
            if (mBinding.chipGenderM.isChecked())  gender = "M";
            else if (mBinding.chipGenderF.isChecked()) gender = "F";
            else if (mBinding.chipGenderNb.isChecked()) gender = "NB";

            String units = mBinding.chipMetric.isChecked() ? "metric" : "imperial";

            mViewModel.saveProfile(name, dob, gender, units, (success, message) ->
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                        success ? "Profile saved" : "Save failed: " + message,
                        Toast.LENGTH_SHORT).show();
                })
            );
        });

        // Add site login button
        mBinding.btnAddSite.setOnClickListener(v -> showAddSiteDialog());
    }

    private void observeViewModel() {
        mViewModel.profile.observe(getViewLifecycleOwner(), profile -> {
            if (profile == null) return;
            mBinding.etName.setText(profile.name != null ? profile.name : "");
            mBinding.etDob.setText(profile.dateOfBirth != null ? profile.dateOfBirth : "");

            if ("M".equals(profile.gender))      mBinding.chipGenderM.setChecked(true);
            else if ("F".equals(profile.gender)) mBinding.chipGenderF.setChecked(true);
            else if ("NB".equals(profile.gender))mBinding.chipGenderNb.setChecked(true);

            if ("imperial".equals(profile.preferredUnits)) mBinding.chipImperial.setChecked(true);
            else mBinding.chipMetric.setChecked(true);
        });
    }

    private void showAddSiteDialog() {
        // Show a dialog to enter site URL, username, password
        // Full implementation uses a custom dialog layout
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.profile_add_site));
        builder.setMessage("Site credential management coming in the next build.");
        builder.setPositiveButton(getString(R.string.ok), null);
        builder.show();
    }

    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        CharSequence text = et.getText();
        return text != null ? text.toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
