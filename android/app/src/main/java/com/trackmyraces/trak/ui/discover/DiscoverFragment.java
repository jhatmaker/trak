package com.trackmyraces.trak.ui.discover;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
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
 * Searches popular running result sites for the runner immediately after profile setup
 * (or when "Search all sources now" is tapped from Profile).
 *
 * Found sites are persisted as pending matches in Room (idempotent). The cards shown
 * here give a quick preview; the runner reviews and confirms matches via
 * PendingMatchesFragment (accessible from the Dashboard banner).
 *
 * Navigation:
 *   - "View on [site]" buttons open the URL in the system browser — no navigation to
 *     AddResultFragment here. Use the Dashboard "Results found" banner to add results.
 *   - "Done" / "Skip" always pops back to wherever the user came from (Profile or Dashboard).
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

        // Always pops back to origin (Profile or Dashboard) — never hardcoded dashboard
        mBinding.btnSkip.setOnClickListener(v ->
            Navigation.findNavController(requireView()).popBackStack());

        // Read nav args
        Bundle args            = getArguments();
        String runnerName      = args != null ? args.getString("runnerName",    "") : "";
        String dateOfBirth     = args != null ? args.getString("dateOfBirth",   "") : "";
        String interestsCsv    = args != null ? args.getString("interests",     "") : "";
        String excludeCsv      = args != null ? args.getString("excludeSiteIds","") : "";
        String sinceDate       = args != null ? args.getString("sinceDate",     "") : "";

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
        mBinding.layoutSites.setVisibility(View.GONE);
        mBinding.tvError.setVisibility(View.GONE);

        // DiscoverFragment always does a full extraction (extractResults=true).
        // sinceDate is null on first run (full history, capped at 50) or set for incremental updates.
        // Full extraction from UI — pass empty lastKnownCounts (no pre-check, always extract)
        mRepo.discoverResults(runnerName, dateOfBirth, interests, excludeIds, true, sinceDate,
            java.util.Collections.emptyMap(),
            new RaceResultRepository.RepositoryCallback<DiscoverResponse>() {
                @Override
                public void onSuccess(DiscoverResponse response) {
                    requireActivity().runOnUiThread(() -> showResults(response));
                }
                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> showError(message));
                }
            });
    }

    private void showResults(DiscoverResponse response) {
        mBinding.layoutSearching.setVisibility(View.GONE);
        mBinding.layoutSites.setVisibility(View.VISIBLE);

        // Change "Skip" to "Done" now that we have results
        mBinding.btnSkip.setText(getString(R.string.discover_done));

        if (response.sites == null || response.sites.isEmpty()) {
            showError(getString(R.string.discover_no_results));
            return;
        }

        // Store updated site counts for future cheap pre-check comparisons
        if (response.siteResultCounts != null && !response.siteResultCounts.isEmpty()) {
            PollScheduler.storeSiteCounts(requireContext(), response.siteResultCounts);
        }

        // Persist found sites as pending matches (idempotent — safe to call on re-poll)
        List<DiscoverSiteResult> found = new ArrayList<>();
        for (DiscoverSiteResult s : response.sites) {
            if (s.found) found.add(s);
        }
        if (!found.isEmpty()) {
            mRepo.savePendingMatches(found, mRunnerName);
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DiscoverSiteResult site : response.sites) {
            View card = inflater.inflate(R.layout.item_discover_site, mBinding.layoutSites, false);

            TextView       tvName  = card.findViewById(R.id.tv_site_name);
            TextView       tvBadge = card.findViewById(R.id.tv_badge);
            TextView       tvNotes = card.findViewById(R.id.tv_notes);
            MaterialButton btnView = card.findViewById(R.id.btn_view);

            tvName.setText(site.siteName);

            if (site.found) {
                tvBadge.setText(site.resultCount > 0
                    ? getResources().getQuantityString(R.plurals.discover_result_count,
                        site.resultCount, site.resultCount)
                    : getString(R.string.discover_found_badge));
                tvBadge.setBackgroundColor(requireContext().getColor(R.color.trak_primary));
                tvBadge.setVisibility(View.VISIBLE);

                if (site.notes != null && !site.notes.isEmpty()) {
                    tvNotes.setText(site.notes);
                    tvNotes.setVisibility(View.VISIBLE);
                }

                // Open in browser — not in AddResultFragment — so the back stack stays clean
                if (site.resultsUrl != null) {
                    btnView.setText(getString(R.string.discover_view_results));
                    btnView.setVisibility(View.VISIBLE);
                    btnView.setOnClickListener(v ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(site.resultsUrl))));
                }
            } else {
                tvBadge.setText(getString(R.string.discover_not_found_badge));
                tvBadge.setBackgroundColor(requireContext().getColor(R.color.text_secondary));
                tvBadge.setVisibility(View.VISIBLE);
            }

            mBinding.layoutSites.addView(card);
        }

        // If any matches were found, change "Done" subtitle to hint about the dashboard banner
        if (!found.isEmpty()) {
            mBinding.btnSkip.setText(getString(R.string.discover_done));
        }
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
