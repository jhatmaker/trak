package com.trackmyraces.trak.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.work.WorkInfo;

import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;
import com.trackmyraces.trak.data.repository.SourcesRepository;
import com.trackmyraces.trak.sync.PollScheduler;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.trackmyraces.trak.data.repository.RaceResultRepository.RepositoryCallback;

public class ProfileViewModel extends AndroidViewModel {

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
    }

    /** Returns the current hidden site IDs synchronously for use at navigation time. */
    public List<String> getHiddenSiteIdsNow() {
        List<String> current = hiddenSiteIds.getValue();
        return current != null ? current : Collections.emptyList();
    }

    public interface SaveCallback {
        void onResult(boolean success, String message);
    }

    public void saveProfile(String name, String dob, String gender,
                            String units, String tempUnit, String interests, SaveCallback callback) {
        // getProfileSync() is a blocking DB read — must run off the main thread
        mExecutor.execute(() -> {
            RunnerProfileEntity current = mRepo.getProfileSync();

            RunnerProfileEntity entity = current != null ? current : new RunnerProfileEntity();
            if (entity.id == null) entity.id = UUID.randomUUID().toString();
            entity.name           = name;
            entity.dateOfBirth    = dob;
            entity.gender         = gender;
            entity.preferredUnits    = units;
            entity.preferredTempUnit = tempUnit;
            entity.interests         = interests;
            entity.status         = "active";

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
