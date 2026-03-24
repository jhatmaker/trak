package com.trackmyraces.trak.ui.profile;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.databinding.FragmentProfileBinding;

import java.util.Calendar;
import java.util.Locale;

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
        mBinding.etDob.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            // Parse existing DOB if set
            String existing = mBinding.etDob.getText() != null
                ? mBinding.etDob.getText().toString() : "";
            if (existing.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String[] parts = existing.split("-");
                cal.set(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2]));
            } else {
                cal.add(Calendar.YEAR, -30); // default to 30 years ago
            }

            new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> {
                    String dob = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                    mBinding.etDob.setText(dob);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });
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
