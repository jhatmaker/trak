package com.trackmyraces.trak.ui.claims;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.dto.ExtractionResponse;
import com.trackmyraces.trak.databinding.FragmentAddResultBinding;
import com.trackmyraces.trak.ui.NetworkAwareFragment;
import com.trackmyraces.trak.ui.profile.ProfileViewModel;
import com.trackmyraces.trak.util.NetworkState;

/**
 * AddResultFragment
 *
 * Drives the three-step Add Result flow by observing ClaimViewModel.state:
 *
 *   IDLE       → show URL input form
 *   EXTRACTING → hide form, show spinner + message
 *   REVIEW     → hide spinner, show extracted result fields for review
 *   CLAIMING   → disable Claim button, show spinner
 *   SUCCESS    → navigate to result detail
 *   ERROR      → show error message, return to IDLE
 */
public class AddResultFragment extends NetworkAwareFragment {

    private FragmentAddResultBinding mBinding;
    private ClaimViewModel           mViewModel;
    private RunnerProfileEntity      mProfile;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentAddResultBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ClaimViewModel.class);

        // Observe profile via activity scope so it's loaded regardless of which tab was visited first
        new ViewModelProvider(requireActivity()).get(ProfileViewModel.class)
            .profile.observe(getViewLifecycleOwner(), profile -> {
                mProfile = profile;
                // Pre-fill name field if still blank
                if (profile != null && profile.name != null && !profile.name.isEmpty()
                        && getText(mBinding.etName).isEmpty()) {
                    mBinding.etName.setText(profile.name);
                }
            });

        // Pre-fill URL if navigated from DiscoverFragment
        if (getArguments() != null) {
            String prefillUrl = getArguments().getString("prefillUrl");
            if (prefillUrl != null && !prefillUrl.isEmpty()) {
                mBinding.etUrl.setText(prefillUrl);
            }
        }

        setupClickListeners();
        observeViewModel();
    }

    private void setupClickListeners() {
        // Step 1: Extract
        mBinding.btnExtract.setOnClickListener(v -> {
            // Require a complete profile before extraction — name is used to match results
            if (mProfile == null || mProfile.name == null || mProfile.name.isEmpty()
                    || mProfile.dateOfBirth == null || mProfile.dateOfBirth.isEmpty()) {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Profile required")
                    .setMessage("Please complete your profile (name and date of birth) before finding results. "
                        + "Your name is used to search the results page, and your date of birth is needed "
                        + "for age group calculation.")
                    .setPositiveButton("Set up profile", (d, w) ->
                        Navigation.findNavController(requireView())
                            .navigate(R.id.profileFragment))
                    .setNegativeButton("Cancel", null)
                    .show();
                return;
            }

            hideKeyboard();
            String url     = getText(mBinding.etUrl);
            // Pre-fill name from profile if the field is empty
            String name    = getText(mBinding.etName);
            if (name.isEmpty()) name = mProfile.name;
            String bib     = getText(mBinding.etBib);
            String context = getText(mBinding.etContext);
            mViewModel.extract(url, name, bib.isEmpty() ? null : bib,
                               null, context.isEmpty() ? null : context);
        });

        // Manual entry — navigate to manual entry form
        mBinding.btnManual.setOnClickListener(v ->
            Navigation.findNavController(requireView())
                .navigate(R.id.action_add_to_manual));

        // Step 3: Claim
        mBinding.btnClaim.setOnClickListener(v -> mViewModel.confirmClaim());

        // Back to input from review
        mBinding.btnBackToInput.setOnClickListener(v -> mViewModel.reset());
    }

    @Override
    protected void onNetworkStateChanged(@NonNull NetworkState state) {
        boolean online = state.isOnline();
        setViewOnlineOnly(mBinding.btnExtract, online);
        mBinding.tvOfflineHint.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    private void observeViewModel() {
        mViewModel.state.observe(getViewLifecycleOwner(), this::applyState);

        mViewModel.extraction.observe(getViewLifecycleOwner(), extraction -> {
            if (extraction != null) populateReviewFields(extraction);
        });

        mViewModel.errorMsg.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                mBinding.tvError.setText(msg);
                mBinding.tvError.setVisibility(View.VISIBLE);
            } else {
                mBinding.tvError.setVisibility(View.GONE);
            }
        });

        mViewModel.claimResult.observe(getViewLifecycleOwner(), claimResponse -> {
            if (claimResponse != null && claimResponse.resultId != null) {
                Bundle args = new Bundle();
                args.putString("resultId", claimResponse.resultId);
                Navigation.findNavController(requireView())
                    .navigate(R.id.action_add_to_detail, args);
            }
        });
    }

    private void applyState(ClaimViewModel.ClaimState state) {
        // Hide all panels first
        mBinding.layoutInput.setVisibility(View.GONE);
        mBinding.layoutLoading.setVisibility(View.GONE);
        mBinding.layoutReview.setVisibility(View.GONE);
        mBinding.tvError.setVisibility(View.GONE);

        switch (state) {
            case IDLE:
                mBinding.layoutInput.setVisibility(View.VISIBLE);
                break;
            case EXTRACTING:
                mBinding.layoutLoading.setVisibility(View.VISIBLE);
                break;
            case REVIEW:
                mBinding.layoutReview.setVisibility(View.VISIBLE);
                break;
            case CLAIMING:
                mBinding.layoutReview.setVisibility(View.VISIBLE);
                mBinding.btnClaim.setEnabled(false);
                mBinding.btnClaim.setText(R.string.add_claiming);
                break;
            case SUCCESS:
                // Navigation handled in claimResult observer above
                break;
            case ERROR:
                mBinding.layoutInput.setVisibility(View.VISIBLE);
                mBinding.tvError.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void populateReviewFields(ExtractionResponse extraction) {
        // Race summary (read-only)
        mBinding.tvReviewRaceName.setText(extraction.raceName != null ? extraction.raceName : "");
        String dateDist = (extraction.raceDate != null ? extraction.raceDate : "")
            + (extraction.distanceLabel != null ? "  ·  " + extraction.distanceLabel : "");
        mBinding.tvReviewDateDist.setText(dateDist);

        // Pre-fill editable fields from ViewModel (two-way bound)
        if (extraction.finishTime != null)    mBinding.etReviewTime.setText(extraction.finishTime);
        if (extraction.ageGroupLabel != null) mBinding.etReviewAg.setText(extraction.ageGroupLabel);
        if (extraction.bibNumber != null)     { /* bib shown in race summary */ }

        // Wire editable fields back to ViewModel
        mBinding.etReviewTime.addTextChangedListener(new SimpleTextWatcher(
            s -> mViewModel.editFinishTime.setValue(s)));
        mBinding.etReviewAg.addTextChangedListener(new SimpleTextWatcher(
            s -> mViewModel.editAgeGroupLabel.setValue(s)));
        mBinding.etReviewNotes.addTextChangedListener(new SimpleTextWatcher(
            s -> mViewModel.editNotes.setValue(s)));

        mBinding.btnClaim.setEnabled(true);
        mBinding.btnClaim.setText(R.string.add_claim_button);
    }

    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        CharSequence text = et.getText();
        return text != null ? text.toString().trim() : "";
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    /** Minimal TextWatcher adapter — only onTextChanged matters for us. */
    private static class SimpleTextWatcher implements android.text.TextWatcher {
        interface Callback { void onText(String s); }
        private final Callback mCallback;
        SimpleTextWatcher(Callback cb) { mCallback = cb; }
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
            mCallback.onText(s.toString());
        }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
