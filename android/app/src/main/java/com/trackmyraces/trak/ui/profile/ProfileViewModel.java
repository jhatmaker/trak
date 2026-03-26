package com.trackmyraces.trak.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.trackmyraces.trak.data.repository.RaceResultRepository.RepositoryCallback;

public class ProfileViewModel extends AndroidViewModel {

    private final RunnerProfileRepository mRepo;
    private final ExecutorService         mExecutor = Executors.newSingleThreadExecutor();
    public  final LiveData<RunnerProfileEntity> profile;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        mRepo   = new RunnerProfileRepository(application);
        profile = mRepo.getProfile();
    }

    public interface SaveCallback {
        void onResult(boolean success, String message);
    }

    public void saveProfile(String name, String dob, String gender,
                            String units, String interests, SaveCallback callback) {
        // getProfileSync() is a blocking DB read — must run off the main thread
        mExecutor.execute(() -> {
            RunnerProfileEntity current = mRepo.getProfileSync();

            RunnerProfileEntity entity = current != null ? current : new RunnerProfileEntity();
            if (entity.id == null) entity.id = UUID.randomUUID().toString();
            entity.name           = name;
            entity.dateOfBirth    = dob;
            entity.gender         = gender;
            entity.preferredUnits = units;
            entity.interests      = interests;
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
