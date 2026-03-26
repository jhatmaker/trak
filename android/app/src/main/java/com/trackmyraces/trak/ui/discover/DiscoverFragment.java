package com.trackmyraces.trak.ui.discover;

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

/**
 * DiscoverFragment
 *
 * Searches popular running result sites for the runner immediately after profile setup.
 * Receives runnerName and dateOfBirth as nav arguments, makes one POST /discover call,
 * then shows a card per site with a "View results" button that pre-fills AddResultFragment.
 *
 * Navigation args: runnerName (required), dateOfBirth (optional)
 */
public class DiscoverFragment extends Fragment {

    private FragmentDiscoverBinding mBinding;
    private RaceResultRepository    mRepo;

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
            Navigation.findNavController(requireView())
                .navigate(R.id.action_discover_to_dashboard));

        // Read nav args
        String runnerName  = DiscoverFragmentArgs.fromBundle(getArguments()).getRunnerName();
        String dateOfBirth = DiscoverFragmentArgs.fromBundle(getArguments()).getDateOfBirth();

        startDiscovery(runnerName, dateOfBirth);
    }

    private void startDiscovery(String runnerName, String dateOfBirth) {
        mBinding.layoutSearching.setVisibility(View.VISIBLE);
        mBinding.layoutSites.setVisibility(View.GONE);
        mBinding.tvError.setVisibility(View.GONE);

        mRepo.discoverResults(runnerName, dateOfBirth,
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

        if (response.sites == null || response.sites.isEmpty()) {
            showError(getString(R.string.discover_no_results));
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DiscoverSiteResult site : response.sites) {
            View card = inflater.inflate(R.layout.item_discover_site, mBinding.layoutSites, false);

            TextView tvName  = card.findViewById(R.id.tv_site_name);
            TextView tvBadge = card.findViewById(R.id.tv_badge);
            TextView tvNotes = card.findViewById(R.id.tv_notes);
            MaterialButton btnView = card.findViewById(R.id.btn_view);

            tvName.setText(site.siteName);

            if (site.found) {
                // Green "Found" badge
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

                if (site.resultsUrl != null) {
                    btnView.setVisibility(View.VISIBLE);
                    btnView.setOnClickListener(v -> openInAddResult(site));
                }
            } else {
                tvBadge.setText(getString(R.string.discover_not_found_badge));
                tvBadge.setBackgroundColor(requireContext().getColor(R.color.text_secondary));
                tvBadge.setVisibility(View.VISIBLE);
            }

            mBinding.layoutSites.addView(card);
        }
    }

    private void openInAddResult(DiscoverSiteResult site) {
        // Navigate to AddResult with the site's results URL pre-filled
        Bundle args = new Bundle();
        args.putString("prefillUrl", site.resultsUrl);
        args.putString("prefillSource", site.siteName);
        Navigation.findNavController(requireView())
            .navigate(R.id.action_discover_to_add, args);
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
