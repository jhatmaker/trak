package com.trackmyraces.trak.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.work.WorkInfo;

import com.trackmyraces.trak.TrakApplication;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;
import com.trackmyraces.trak.data.repository.SourcesRepository;
import com.trackmyraces.trak.sync.PollScheduler;
import com.trackmyraces.trak.util.NetworkMonitor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.trackmyraces.trak.data.repository.RaceResultRepository.RepositoryCallback;

public class ProfileViewModel extends AndroidViewModel {

    /** Three-state sync indicator for the profile screen chip. */
    public enum SyncState {
        SYNCED,   // online, profile synced, zero unsynced results
        PENDING,  // online but local changes not yet pushed/pulled
        OFFLINE   // no network connection
    }

    private final RunnerProfileRepository mRepo;
    private final SourcesRepository       mSourcesRepo;
    private final ExecutorService         mExecutor = Executors.newSingleThreadExecutor();

    public final LiveData<RunnerProfileEntity> profile;

    /**
     * Live list of hidden default site IDs — passed to /discover so those sites are skipped.
     * Null/empty means all default sites are searched.
     */
    public final LiveData<List<String>> hiddenSiteIds;

    /**
     * Live count of currently-enabled default sources — drives the "Search all (N) enabled
     * sources now" button label.
     */
    public final LiveData<Integer> hiddenDefaultSiteCount;

    /**
     * Total enabled source count = (TOTAL_DEFAULT_SITES − hidden defaults) + enabled custom sources.
     * Observed by ProfileFragment to keep the poll button label accurate.
     */
    public final LiveData<Integer> enabledSourceCount;

    /**
     * LiveData of WorkInfo for the pending delayed poll.
     * ENQUEUED → user sees "Search scheduled"; SUCCEEDED/CANCELLED → button resets.
     */
    public final LiveData<List<WorkInfo>> pendingPollWorkInfo;

    /** Live count of results not yet synced — used to show "N pending" on the sync chip. */
    public final LiveData<Integer> unsyncedCount;

    /**
     * Combined sync state for the profile screen chip.
     * Hidden for free users (profile.userId == null).
     */
    public final LiveData<SyncState> syncState;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        mRepo        = new RunnerProfileRepository(application);
        mSourcesRepo = new SourcesRepository(application);
        profile                = mRepo.getProfile();
        hiddenSiteIds          = mSourcesRepo.getHiddenDefaultSiteIdsLive();
        hiddenDefaultSiteCount = mSourcesRepo.getHiddenDefaultSiteCount();
        pendingPollWorkInfo    = PollScheduler.getPendingPollWorkInfo(application);

        // Combine hidden-default count + enabled-custom count into a single total
        LiveData<Integer> customCount = mSourcesRepo.getEnabledCustomSourceCount();
        MediatorLiveData<Integer> combined = new MediatorLiveData<>();
        Runnable recompute = () -> {
            Integer hidden = hiddenDefaultSiteCount.getValue();
            Integer custom = customCount.getValue();
            int defaults = SourcesRepository.TOTAL_DEFAULT_SITES - (hidden != null ? hidden : 0);
            combined.setValue(defaults + (custom != null ? custom : 0));
        };
        combined.addSource(hiddenDefaultSiteCount, v -> recompute.run());
        combined.addSource(customCount,            v -> recompute.run());
        enabledSourceCount = combined;

        // Sync state — combines network, profile.isSynced, and unsynced result count
        RaceResultRepository resultRepo = new RaceResultRepository(application);
        LiveData<Boolean>  networkLive     = TrakApplication.getInstance().getNetworkMonitor();
        LiveData<Integer>  unsyncedLive    = resultRepo.getUnsyncedCountLive();
        unsyncedCount = unsyncedLive;
        MediatorLiveData<SyncState> syncMed = new MediatorLiveData<>();
        Runnable recomputeSync = () -> {
            Boolean online   = networkLive.getValue();
            if (online == null || !online) {
                syncMed.setValue(SyncState.OFFLINE);
                return;
            }
            RunnerProfileEntity p = profile.getValue();
            boolean profileSynced = (p == null || p.isSynced);
            Integer unsynced      = unsyncedLive.getValue();
            boolean resultsSynced = (unsynced == null || unsynced == 0);
            syncMed.setValue((profileSynced && resultsSynced) ? SyncState.SYNCED : SyncState.PENDING);
        };
        syncMed.addSource(networkLive,   v -> recomputeSync.run());
        syncMed.addSource(profile,       v -> recomputeSync.run());
        syncMed.addSource(unsyncedLive,  v -> recomputeSync.run());
        syncState = syncMed;
    }

    /**
     * Returns the list of enabled source GUIDs for the current profile.
     * Must be called from a background thread (DB read).
     */
    public List<String> getEnabledSourceGuidsNow() {
        return mSourcesRepo.getEnabledSourceGuidsSync();
    }

    /** @deprecated Use getEnabledSourceGuidsNow() for the new GUID-based API. */
    @Deprecated
    public List<String> getHiddenSiteIdsNow() {
        List<String> current = hiddenSiteIds.getValue();
        return current != null ? current : Collections.emptyList();
    }

    /**
     * Returns the userId for the current profile.
     * Falls back to mLastSavedUserId in the rare window between a save completing
     * on the executor thread and the Room LiveData updating on the main thread.
     */
    private volatile String mLastSavedUserId = null;

    public String getUserId() {
        RunnerProfileEntity p = profile.getValue();
        if (p != null && p.userId != null) return p.userId;
        return mLastSavedUserId;
    }

    public interface SaveCallback {
        void onResult(boolean success, String message);
    }

    public void saveProfile(String name, String dob, String gender,
                            String units, String tempUnit, String interests,
                            int targetPaceSecondsPerMile, SaveCallback callback) {
        // getProfileSync() is a blocking DB read — must run off the main thread
        mExecutor.execute(() -> {
            RunnerProfileEntity current = mRepo.getProfileSync();

            RunnerProfileEntity entity = current != null ? current : new RunnerProfileEntity();
            if (entity.id == null) entity.id = UUID.randomUUID().toString();
            if (entity.userId == null) entity.userId = UUID.randomUUID().toString();
            mLastSavedUserId = entity.userId;
            entity.name           = name;
            entity.dateOfBirth    = dob;
            entity.gender         = gender;
            entity.preferredUnits             = units;
            entity.preferredTempUnit          = tempUnit;
            entity.interests                  = interests;
            entity.targetPaceSecondsPerMile   = targetPaceSecondsPerMile;
            entity.status                     = "active";

            RepositoryCallback<com.trackmyraces.trak.data.network.dto.ProfileResponse> cb =
                new RepositoryCallback<com.trackmyraces.trak.data.network.dto.ProfileResponse>() {
                    @Override public void onSuccess(com.trackmyraces.trak.data.network.dto.ProfileResponse r) { callback.onResult(true, null); }
                    @Override public void onError(String m) { callback.onResult(false, m); }
                };

            if (current == null) {
                mRepo.createProfile(entity, cb);
            } else {
                mRepo.updateProfile(entity, cb);
            }
        });
    }
}
