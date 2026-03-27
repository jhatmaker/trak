package com.trackmyraces.trak.ui.dismissed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.databinding.FragmentDismissedMatchesBinding;

import java.util.List;

/**
 * DismissedMatchesFragment
 *
 * Shows all races the runner previously marked "Not me". Accessible from two places:
 *   - Profile → "View dismissed results" (shows all sites)
 *   - Manage Sources → per-source "View dismissed" link (filters to one site)
 *
 * Each card has a "Restore to pending" button that moves the entry back to the
 * pending queue so the runner can claim or re-dismiss it.
 *
 * Navigation args:
 *   siteId   — optional; if set, only shows dismissed matches for that site
 *   siteName — optional; used in the empty-state message
 */
public class DismissedMatchesFragment extends Fragment {

    private FragmentDismissedMatchesBinding mBinding;
    private DismissedMatchesViewModel       mViewModel;
    private DismissedMatchAdapter           mAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentDismissedMatchesBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String siteId = getArguments() != null ? getArguments().getString("siteId", "") : "";

        mViewModel = new ViewModelProvider(this,
            DismissedMatchesViewModel.factory(requireActivity().getApplication(),
                siteId.isEmpty() ? null : siteId))
            .get(DismissedMatchesViewModel.class);

        mAdapter = new DismissedMatchAdapter(match -> {
            mViewModel.restore(match);
            String label = (match.raceName != null && !match.raceName.isEmpty())
                ? match.raceName
                : (match.siteName != null ? match.siteName : "Result");
            Toast.makeText(requireContext(),
                label + " " + getString(R.string.dismissed_restored_toast),
                Toast.LENGTH_SHORT).show();
        });

        mBinding.rvDismissed.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvDismissed.setAdapter(mAdapter);

        mViewModel.dismissedMatches.observe(getViewLifecycleOwner(), this::onMatchesChanged);
    }

    private void onMatchesChanged(List<PendingMatchEntity> matches) {
        if (matches == null || matches.isEmpty()) {
            mBinding.rvDismissed.setVisibility(View.GONE);
            mBinding.tvEmpty.setVisibility(View.VISIBLE);
        } else {
            mBinding.rvDismissed.setVisibility(View.VISIBLE);
            mBinding.tvEmpty.setVisibility(View.GONE);
            mAdapter.submitList(matches);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
