package com.trackmyraces.trak.ui.pending;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;

import java.util.List;

/**
 * ViewModel for PendingMatchesFragment.
 * Exposes pending matches and claim/dismiss actions.
 */
public class PendingMatchesViewModel extends AndroidViewModel {

    private final RaceResultRepository mRepo;

    public final LiveData<List<PendingMatchEntity>> pendingMatches;

    public PendingMatchesViewModel(@NonNull Application application) {
        super(application);
        mRepo          = new RaceResultRepository(application);
        pendingMatches = mRepo.getPendingMatches();
    }

    public void dismiss(PendingMatchEntity match) {
        mRepo.dismissPendingMatch(match.id);
    }

    /**
     * Mark as claimed and trigger AI extraction for this match.
     * Returns the match so the fragment can navigate to AddResultFragment with the URL.
     */
    public void claim(PendingMatchEntity match) {
        mRepo.claimPendingMatch(match.id);
    }
}
