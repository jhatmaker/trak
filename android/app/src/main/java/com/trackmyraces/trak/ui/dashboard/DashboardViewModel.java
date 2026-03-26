package com.trackmyraces.trak.ui.dashboard;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;

import java.util.List;

/**
 * DashboardViewModel
 *
 * Provides all data for the Dashboard (home) screen:
 *   - Runner name and DOB (from profile)
 *   - Total race count
 *   - Total distance run
 *   - Count of unique races
 *   - Personal records list (one per canonical distance)
 *   - Recent results (last 5)
 */
public class DashboardViewModel extends AndroidViewModel {

    private final RaceResultRepository   mResultRepo;
    private final RunnerProfileRepository mProfileRepo;

    // ── Exposed LiveData ──────────────────────────────────────────────────

    public final LiveData<RunnerProfileEntity>     profile;
    public final LiveData<Integer>                 totalRaceCount;
    public final LiveData<Double>                  totalDistanceMeters;
    public final LiveData<Integer>                 uniqueRaceCount;
    public final LiveData<List<RaceResultEntity>>  prList;
    public final LiveData<List<RaceResultEntity>>  recentResults;

    // Sync state for pull-to-refresh UI feedback
    private final MutableLiveData<SyncState> mSyncState = new MutableLiveData<>(SyncState.IDLE);
    public  final LiveData<SyncState>        syncState  = mSyncState;

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        mResultRepo  = new RaceResultRepository(application);
        mProfileRepo = new RunnerProfileRepository(application);

        profile             = mProfileRepo.getProfile();
        totalRaceCount      = mResultRepo.getTotalCount();
        totalDistanceMeters = mResultRepo.getTotalDistanceMeters();
        uniqueRaceCount     = mResultRepo.getUniqueRaceCount();
        prList              = mResultRepo.getPRs();
        recentResults       = mResultRepo.getRecentResults(5);
    }

    /**
     * Called when user pulls to refresh the dashboard.
     * Triggers a full sync from the backend.
     */
    public void refresh() {
        mSyncState.setValue(SyncState.SYNCING);
        // SyncManager accessed via Application singleton to avoid leaking context
        ((com.trackmyraces.trak.TrakApplication) getApplication())
            .getSyncManager()
            .syncIfOnline((success, message) ->
                mSyncState.postValue(success ? SyncState.SUCCESS : SyncState.ERROR)
            );
    }

    public enum SyncState { IDLE, SYNCING, SUCCESS, ERROR }
}
