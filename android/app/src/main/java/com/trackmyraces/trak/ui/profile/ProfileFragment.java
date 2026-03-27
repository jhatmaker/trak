package com.trackmyraces.trak.ui.profile;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.databinding.FragmentProfileBinding;
import com.trackmyraces.trak.data.repository.SourcesRepository;
import com.trackmyraces.trak.sync.PollScheduler;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ProfileFragment — edit runner profile and manage linked credential sites.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding mBinding;
    private ProfileViewModel       mViewModel;
    private boolean                mIsNewProfile = false; // true when no profile existed before save

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
        setupPollSection();
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

            String units    = mBinding.chipMetric.isChecked()    ? "metric"
                            : mBinding.chipBothUnits.isChecked() ? "both"
                            : "imperial";
            String tempUnit = mBinding.chipCelsius.isChecked()  ? "celsius"
                            : mBinding.chipBothTemp.isChecked() ? "both"
                            : "fahrenheit";
            String interests = getSelectedInterests();

            boolean isFirstSave = mIsNewProfile;
            mViewModel.saveProfile(name, dob, gender, units, tempUnit, interests, (success, message) ->
                requireActivity().runOnUiThread(() -> {
                    if (!success) {
                        Toast.makeText(requireContext(),
                            "Save failed: " + message, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isFirstSave) {
                        // New profile — go search for their existing results
                        String excludeCsv = android.text.TextUtils.join(",",
                            mViewModel.getHiddenSiteIdsNow());
                        Bundle args = new Bundle();
                        args.putString("runnerName",    name);
                        args.putString("dateOfBirth",   dob);
                        args.putString("interests",     interests);
                        args.putString("excludeSiteIds", excludeCsv);
                        Navigation.findNavController(requireView())
                            .navigate(R.id.action_profile_to_discover, args);
                    } else {
                        Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show();
                    }
                })
            );
        });

        // Add site login button
        mBinding.btnAddSite.setOnClickListener(v -> showAddSiteDialog());

        // Manage sources — navigate to dedicated sub-page
        mBinding.btnManageSources.setOnClickListener(v ->
            Navigation.findNavController(requireView())
                .navigate(R.id.action_profile_to_manage_sources));
    }

    private void observeViewModel() {
        // Update poll-now button label with live enabled-source count
        mViewModel.hiddenDefaultSiteCount.observe(getViewLifecycleOwner(), hiddenCount -> {
            int hidden  = hiddenCount != null ? hiddenCount : 0;
            int enabled = SourcesRepository.TOTAL_DEFAULT_SITES - hidden;
            mBinding.btnPollNow.setText(
                getString(R.string.poll_now_button_counted, enabled));
        });

        mViewModel.profile.observe(getViewLifecycleOwner(), profile -> {
            boolean isNewProfile = (profile == null || profile.name == null || profile.name.isEmpty());
            mIsNewProfile = isNewProfile;
            mBinding.tvSetupPrompt.setVisibility(isNewProfile ? View.VISIBLE : View.GONE);

            if (profile == null) return;
            mBinding.etName.setText(profile.name != null ? profile.name : "");
            mBinding.etDob.setText(profile.dateOfBirth != null ? profile.dateOfBirth : "");

            if ("M".equals(profile.gender))      mBinding.chipGenderM.setChecked(true);
            else if ("F".equals(profile.gender)) mBinding.chipGenderF.setChecked(true);
            else if ("NB".equals(profile.gender))mBinding.chipGenderNb.setChecked(true);

            // Distance — default to imperial (miles)
            if ("metric".equals(profile.preferredUnits))       mBinding.chipMetric.setChecked(true);
            else if ("both".equals(profile.preferredUnits))    mBinding.chipBothUnits.setChecked(true);
            else                                               mBinding.chipImperial.setChecked(true);

            // Temperature — default to fahrenheit
            if ("celsius".equals(profile.preferredTempUnit))   mBinding.chipCelsius.setChecked(true);
            else if ("both".equals(profile.preferredTempUnit)) mBinding.chipBothTemp.setChecked(true);
            else                                               mBinding.chipFahrenheit.setChecked(true);

            // Restore interest chips
            java.util.List<String> saved = profile.getInterestList();
            mBinding.chipInterestRoad.setChecked(saved.contains("road"));
            mBinding.chipInterestTrail.setChecked(saved.contains("trail"));
            mBinding.chipInterestUltra.setChecked(saved.contains("ultra"));
            mBinding.chipInterestMarathon.setChecked(saved.contains("marathon"));
            mBinding.chipInterestParkrun.setChecked(saved.contains("parkrun"));
            mBinding.chipInterestTriathlon.setChecked(saved.contains("triathlon"));
            mBinding.chipInterestOcr.setChecked(saved.contains("ocr"));
            mBinding.chipInterestTrack.setChecked(saved.contains("track"));
            mBinding.chipInterestCrosscountry.setChecked(saved.contains("crosscountry"));
        });
    }

    /** Returns a comma-separated string of selected interest tags, empty string if none. */
    private String getSelectedInterests() {
        java.util.List<String> selected = new java.util.ArrayList<>();
        if (mBinding.chipInterestRoad.isChecked())         selected.add("road");
        if (mBinding.chipInterestTrail.isChecked())        selected.add("trail");
        if (mBinding.chipInterestUltra.isChecked())        selected.add("ultra");
        if (mBinding.chipInterestMarathon.isChecked())     selected.add("marathon");
        if (mBinding.chipInterestParkrun.isChecked())      selected.add("parkrun");
        if (mBinding.chipInterestTriathlon.isChecked())    selected.add("triathlon");
        if (mBinding.chipInterestOcr.isChecked())          selected.add("ocr");
        if (mBinding.chipInterestTrack.isChecked())        selected.add("track");
        if (mBinding.chipInterestCrosscountry.isChecked()) selected.add("crosscountry");
        return android.text.TextUtils.join(",", selected);
    }

    private void setupPollSection() {
        // Restore saved schedule chip
        String saved = PollScheduler.getSchedule(requireContext());
        if (PollScheduler.SCHEDULE_DAILY.equals(saved))        mBinding.chipPollDaily.setChecked(true);
        else if (PollScheduler.SCHEDULE_WEEKLY.equals(saved))  mBinding.chipPollWeekly.setChecked(true);
        else                                                    mBinding.chipPollOff.setChecked(true);

        // Schedule chip changes
        mBinding.chipGroupPollSchedule.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String schedule;
            if (id == R.id.chip_poll_daily)       schedule = PollScheduler.SCHEDULE_DAILY;
            else if (id == R.id.chip_poll_weekly)  schedule = PollScheduler.SCHEDULE_WEEKLY;
            else                                   schedule = PollScheduler.SCHEDULE_OFF;

            if (!PollScheduler.SCHEDULE_OFF.equals(schedule)) {
                requestNotificationPermissionIfNeeded();
            }
            PollScheduler.setSchedule(requireContext(), schedule);
        });

        // Poll now — navigate to DiscoverFragment with current profile
        mBinding.btnPollNow.setOnClickListener(v -> {
            RunnerProfileEntity profile = mViewModel.profile.getValue();
            if (profile == null || profile.name == null || profile.name.isEmpty()) {
                Toast.makeText(requireContext(),
                    "Save your profile first", Toast.LENGTH_SHORT).show();
                return;
            }
            String excludeCsv = android.text.TextUtils.join(",",
                mViewModel.getHiddenSiteIdsNow());
            Bundle args = new Bundle();
            args.putString("runnerName",    profile.name);
            args.putString("dateOfBirth",   profile.dateOfBirth != null ? profile.dateOfBirth : "");
            args.putString("interests",     profile.interests   != null ? profile.interests   : "");
            args.putString("excludeSiteIds", excludeCsv);
            Navigation.findNavController(requireView())
                .navigate(R.id.action_profile_to_discover, args);
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
        }
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
