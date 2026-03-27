package com.trackmyraces.trak.ui.pending;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.widget.Toast;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.databinding.FragmentPendingMatchesBinding;

import java.util.List;

/**
 * PendingMatchesFragment
 *
 * Shows all pending discovery matches — sites where the runner's name was found
 * but they haven't confirmed yet. For each match the runner can:
 *
 *   "View"           — open the site's results URL in the browser
 *   "Add to profile" — mark as claimed and navigate to AddResultFragment to
 *                      let AI extract the full result details
 *   "Not me"         — dismiss (hidden from list, never shown again)
 */
public class PendingMatchesFragment extends Fragment {

    private FragmentPendingMatchesBinding mBinding;
    private PendingMatchesViewModel       mViewModel;
    private PendingMatchAdapter           mAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentPendingMatchesBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(PendingMatchesViewModel.class);

        mAdapter = new PendingMatchAdapter(new PendingMatchAdapter.Listener() {
            @Override
            public void onClaim(PendingMatchEntity match) {
                // Save the result directly from pending match data — no API call needed.
                // All race data was already extracted during discovery.
                mViewModel.claimAndSave(match);
                String label = (match.raceName != null && !match.raceName.isEmpty())
                    ? match.raceName
                    : (match.siteName != null ? match.siteName : "Result");
                Toast.makeText(requireContext(),
                    label + " added to your history", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDismiss(PendingMatchEntity match) {
                mViewModel.dismiss(match);
            }
        });

        mBinding.rvPendingMatches.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.rvPendingMatches.setAdapter(mAdapter);

        mViewModel.pendingMatches.observe(getViewLifecycleOwner(), this::onMatchesChanged);
    }

    private void onMatchesChanged(List<PendingMatchEntity> matches) {
        if (matches == null || matches.isEmpty()) {
            mBinding.rvPendingMatches.setVisibility(View.GONE);
            mBinding.tvEmpty.setVisibility(View.VISIBLE);
        } else {
            mBinding.rvPendingMatches.setVisibility(View.VISIBLE);
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
