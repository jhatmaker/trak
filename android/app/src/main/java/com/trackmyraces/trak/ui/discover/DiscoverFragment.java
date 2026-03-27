package com.trackmyraces.trak.ui.discover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.network.dto.DiscoverResponse;
import com.trackmyraces.trak.data.network.dto.DiscoverSiteResult;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.databinding.FragmentDiscoverBinding;
import com.trackmyraces.trak.sync.PollScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * DiscoverFragment
 *
 * Searches configured running sites for the runner. Displays a spinner while
 * searching, then either:
 *   - Pops back to the dashboard automatically if results were found
 *     (the dashboard "Results found" banner prompts the runner to review).
 *   - Shows a "No new results" message with a Back button if nothing was found.
 *
 * Results are persisted as PendingMatchEntity rows in Room before navigating away.
 */
public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding mBinding;
    private RaceResultRepository    mRepo;
    private String                  mRunnerName;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentDiscoverBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRepo = new RaceResultRepository(requireActivity().getApplication());

        mBinding.btnSkip.setOnClickListener(v ->
            Navigation.findNavController(requireView()).popBackStack());

        Bundle args         = getArguments();
        String runnerName   = args != null ? args.getString("runnerName",    "") : "";
        String dateOfBirth  = args != null ? args.getString("dateOfBirth",   "") : "";
        String interestsCsv = args != null ? args.getString("interests",     "") : "";
        String excludeCsv   = args != null ? args.getString("excludeSiteIds","") : "";
        String sinceDate    = args != null ? args.getString("sinceDate",     "") : "";

        List<String> interests = new ArrayList<>();
        if (!interestsCsv.isEmpty()) {
            for (String tag : interestsCsv.split(",")) {
                if (!tag.trim().isEmpty()) interests.add(tag.trim());
            }
        }

        List<String> excludeIds = new ArrayList<>();
        if (!excludeCsv.isEmpty()) {
            for (String id : excludeCsv.split(",")) {
                if (!id.trim().isEmpty()) excludeIds.add(id.trim());
            }
        }

        mRunnerName = runnerName;
        String sinceDateArg = (sinceDate != null && !sinceDate.isEmpty()) ? sinceDate : null;
        startDiscovery(runnerName, dateOfBirth, interests, excludeIds, sinceDateArg);
    }

    private void startDiscovery(String runnerName, String dateOfBirth,
                                List<String> interests, List<String> excludeIds,
                                String sinceDate) {
        mBinding.layoutSearching.setVisibility(View.VISIBLE);
        mBinding.tvNoResults.setVisibility(View.GONE);
        mBinding.tvError.setVisibility(View.GONE);

        mRepo.discoverResults(runnerName, dateOfBirth, interests, excludeIds, true, sinceDate,
            java.util.Collections.emptyMap(),
            new RaceResultRepository.RepositoryCallback<DiscoverResponse>() {
                @Override
                public void onSuccess(DiscoverResponse response) {
                    requireActivity().runOnUiThread(() -> handleResults(response));
                }
                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> showError(message));
                }
            });
    }

    private void handleResults(DiscoverResponse response) {
        mBinding.layoutSearching.setVisibility(View.GONE);

        // Store updated site counts for future cheap pre-check comparisons
        if (response.siteResultCounts != null && !response.siteResultCounts.isEmpty()) {
            PollScheduler.storeSiteCounts(requireContext(), response.siteResultCounts);
        }

        // Collect found sites and persist as pending matches
        List<DiscoverSiteResult> found = new ArrayList<>();
        if (response.sites != null) {
            for (DiscoverSiteResult s : response.sites) {
                if (s.found) found.add(s);
            }
        }

        if (found.isEmpty()) {
            // Nothing found — show message, let user tap Back
            mBinding.tvNoResults.setVisibility(View.VISIBLE);
            mBinding.btnSkip.setText(getString(R.string.ok));
            return;
        }

        // Save results then navigate back — dashboard banner will prompt review
        mRepo.savePendingMatches(found, mRunnerName);
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void showError(String message) {
        mBinding.layoutSearching.setVisibility(View.GONE);
        mBinding.tvError.setText(message);
        mBinding.tvError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
