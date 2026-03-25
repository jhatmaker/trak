package com.trackmyraces.trak.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;

import java.util.UUID;
import com.trackmyraces.trak.data.repository.RaceResultRepository.RepositoryCallback;

public class ProfileViewModel extends AndroidViewModel {

    private final RunnerProfileRepository mRepo;
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
                            String units, SaveCallback callback) {
        RunnerProfileEntity current = mRepo.getProfileSync();

        RunnerProfileEntity entity = current != null ? current : new RunnerProfileEntity();
        if (entity.id == null) entity.id = UUID.randomUUID().toString();
        entity.name           = name;
        entity.dateOfBirth    = dob;
        entity.gender         = gender;
        entity.preferredUnits = units;
        entity.status         = "active";

        if (current == null) {
            mRepo.createProfile(entity, new RaceResultRepository.RepositoryCallback<com.trackmyraces.trak.data.network.dto.ProfileResponse>() {
                @Override public void onSuccess(com.trackmyraces.trak.data.network.dto.ProfileResponse r) { callback.onResult(true, null); }
                @Override public void onError(String m)   { callback.onResult(false, m);  }
            });
        } else {
            mRepo.updateProfile(entity, new RaceResultRepository.RepositoryCallback<com.trackmyraces.trak.data.network.dto.ProfileResponse>() {
                @Override public void onSuccess(com.trackmyraces.trak.data.network.dto.ProfileResponse r) { callback.onResult(true, null); }
                @Override public void onError(String m)   { callback.onResult(false, m);  }
            });
        }
    }
}
