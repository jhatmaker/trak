package com.trackmyraces.trak.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.db.dao.RunnerProfileDao;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.data.network.TrakApiService;
import com.trackmyraces.trak.data.network.dto.ProfileRequest;
import com.trackmyraces.trak.data.network.dto.ProfileResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * RunnerProfileRepository
 *
 * Manages the runner's profile — local Room copy + backend sync.
 * The app has exactly one profile per device.
 */
public class RunnerProfileRepository {

    private final RunnerProfileDao mDao;
    private final TrakApiService   mApi;
    private final ExecutorService  mExecutor;

    public RunnerProfileRepository(Application application) {
        TrakDatabase db = TrakDatabase.getInstance(application);
        mDao      = db.runnerProfileDao();
        mApi      = new ApiClient(application).getService();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    /** Observe the local profile — updates automatically when Room changes */
    public LiveData<RunnerProfileEntity> getProfile() {
        return mDao.getProfile();
    }

    /** Returns true if a profile exists locally */
    public boolean hasProfile() {
        return mDao.getProfileCount() > 0;
    }

    /**
     * Create a new profile — writes locally first, then syncs to backend.
     */
    public void createProfile(RunnerProfileEntity profile,
                              RaceResultRepository.RepositoryCallback<ProfileResponse> callback) {
        // Write locally first so the UI is instantly responsive
        mExecutor.execute(() -> {
            profile.isSynced = false;
            mDao.insertOrReplace(profile);
        });

        // Build request for backend
        ProfileRequest request = new ProfileRequest();
        request.name           = profile.name;
        request.dateOfBirth    = profile.dateOfBirth;
        request.gender         = profile.gender;
        request.city           = profile.city;
        request.state          = profile.state;
        request.country        = profile.country;
        request.preferredUnits = profile.preferredUnits;

        mApi.createProfile(request).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    mExecutor.execute(() -> {
                        profile.isSynced = true;
                        mDao.insertOrReplace(profile);
                    });
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Profile sync failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                // Local copy already saved — profile works offline
                callback.onError("Saved locally — will sync when online");
            }
        });
    }

    /**
     * Update profile fields.
     */
    public void updateProfile(RunnerProfileEntity updated,
                              RaceResultRepository.RepositoryCallback<ProfileResponse> callback) {
        mExecutor.execute(() -> mDao.update(updated));

        ProfileRequest request = new ProfileRequest();
        request.name           = updated.name;
        request.dateOfBirth    = updated.dateOfBirth;
        request.gender         = updated.gender;
        request.city           = updated.city;
        request.state          = updated.state;
        request.country        = updated.country;
        request.preferredUnits = updated.preferredUnits;

        mApi.updateProfile(request).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Update failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    /** Store auth token locally after login */
    public void saveAuthToken(String profileId, String token) {
        mExecutor.execute(() -> mDao.updateAuthToken(profileId, token));
    }
}
