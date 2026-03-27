package com.trackmyraces.trak.ui.discover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
    private String                  mUserId;

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

        Bundle args          = getArguments();
        String userId        = args != null ? args.getString("userId",     "") : "";
        String runnerName    = args != null ? args.getString("runnerName", "") : "";
        String dateOfBirth   = args != null ? args.getString("dateOfBirth","") : "";
        String sourceIdsCsv  = args != null ? args.getString("sourceIds",  "") : "";
        String sinceDate     = args != null ? args.getString("sinceDate",  "") : "";

        mUserId = (userId != null && !userId.isEmpty()) ? userId : null;

        List<String> sourceIds = new ArrayList<>();
        if (sourceIdsCsv != null && !sourceIdsCsv.isEmpty()) {
            for (String id : sourceIdsCsv.split(",")) {
                if (!id.trim().isEmpty()) sourceIds.add(id.trim());
            }
        }

        mRunnerName = runnerName;
        String sinceDateArg = (sinceDate != null && !sinceDate.isEmpty()) ? sinceDate : null;
        startDiscovery(runnerName, dateOfBirth, sourceIds, sinceDateArg);
    }

    private void startDiscovery(String runnerName, String dateOfBirth,
                                List<String> sourceIds, String sinceDate) {
        mBinding.layoutSearching.setVisibility(View.VISIBLE);
        mBinding.tvNoResults.setVisibility(View.GONE);
        mBinding.tvError.setVisibility(View.GONE);

        mRepo.discoverResults(mUserId, sourceIds, runnerName, dateOfBirth, true, sinceDate,
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
            Toast.makeText(requireContext(),
                getString(R.string.discover_no_new_results_toast),
                Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).popBackStack();
            return;
        }

        // Save results — callback fires on executor thread with actual pending count after DB writes.
        // Dedup: if all found results were already in the DB (claimed/dismissed), count may be 0.
        mRepo.savePendingMatches(found, mRunnerName,
            new RaceResultRepository.RepositoryCallback<Integer>() {
                @Override
                public void onSuccess(Integer pendingCount) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (pendingCount > 0) {
                            Toast.makeText(requireContext(),
                                getResources().getQuantityString(
                                    R.plurals.discover_results_found_toast,
                                    pendingCount, pendingCount),
                                Toast.LENGTH_LONG).show();
                            Navigation.findNavController(requireView())
                                .navigate(R.id.action_discover_to_dashboard);
                        } else {
                            // API found results but they were all already claimed/dismissed
                            Toast.makeText(requireContext(),
                                getString(R.string.discover_no_new_results_toast),
                                Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).popBackStack();
                        }
                    });
                }
                @Override
                public void onError(String message) { /* DB writes don't fail */ }
            });
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
