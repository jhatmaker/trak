package com.trackmyraces.trak.ui.pending;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;

import java.util.List;

/**
 * ViewModel for PendingMatchesFragment.
 * Exposes pending matches and claim/dismiss actions.
 */
public class PendingMatchesViewModel extends AndroidViewModel {

    private final RaceResultRepository   mRepo;
    private final RunnerProfileRepository mProfileRepo;

    public final LiveData<List<PendingMatchEntity>> pendingMatches;
    public final LiveData<RunnerProfileEntity>      profile;

    public PendingMatchesViewModel(@NonNull Application application) {
        super(application);
        mRepo          = new RaceResultRepository(application);
        mProfileRepo   = new RunnerProfileRepository(application);
        pendingMatches = mRepo.getPendingMatches();
        profile        = mProfileRepo.getProfile();
    }

    public void dismiss(PendingMatchEntity match) {
        mRepo.dismissPendingMatch(match.id);
    }

    /**
     * Saves the pending match as a RaceResultEntity and marks it claimed — no API call.
     * Uses the runner's target pace to disambiguate distance when label matching fails.
     */
    public void claimAndSave(PendingMatchEntity match) {
        RunnerProfileEntity p = profile.getValue();
        int targetPace = (p != null) ? p.targetPaceSecondsPerMile : 0;
        mRepo.claimAndSave(match, targetPace);
    }
}
