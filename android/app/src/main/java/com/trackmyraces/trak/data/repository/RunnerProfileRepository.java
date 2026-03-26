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
     * Create a new profile — writes locally, reports success immediately, then syncs to backend.
     *
     * The local write is the source of truth. Backend sync is fire-and-forget; a 401 or
     * network failure does not fail the operation (auth may not be set up yet).
     */
    public void createProfile(RunnerProfileEntity profile,
                              RaceResultRepository.RepositoryCallback<ProfileResponse> callback) {
        mExecutor.execute(() -> {
            profile.isSynced = false;
            mDao.insertOrReplace(profile);
            // Report success to the UI as soon as local write is done
            callback.onSuccess(null);
        });

        // Attempt backend sync in background — result is not surfaced to the caller
        ProfileRequest request = buildRequest(profile);
        mApi.createProfile(request).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                if (response.isSuccessful()) {
                    mExecutor.execute(() -> {
                        profile.isSynced = true;
                        mDao.insertOrReplace(profile);
                    });
                }
                // 401/4xx ignored — will retry when auth is available
            }
            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                // Network failure ignored — local copy is already saved
            }
        });
    }

    /**
     * Update profile fields — same offline-first pattern as createProfile.
     */
    public void updateProfile(RunnerProfileEntity updated,
                              RaceResultRepository.RepositoryCallback<ProfileResponse> callback) {
        mExecutor.execute(() -> {
            updated.isSynced = false;
            mDao.update(updated);
            callback.onSuccess(null);
        });

        ProfileRequest request = buildRequest(updated);
        mApi.updateProfile(request).enqueue(new Callback<ProfileResponse>() {
            @Override
            public void onResponse(Call<ProfileResponse> call, Response<ProfileResponse> response) {
                if (response.isSuccessful()) {
                    mExecutor.execute(() -> {
                        updated.isSynced = true;
                        mDao.update(updated);
                    });
                }
            }
            @Override
            public void onFailure(Call<ProfileResponse> call, Throwable t) {
                // Ignored — local copy already updated
            }
        });
    }

    private ProfileRequest buildRequest(RunnerProfileEntity e) {
        ProfileRequest r = new ProfileRequest();
        r.name           = e.name;
        r.dateOfBirth    = e.dateOfBirth;
        r.gender         = e.gender;
        r.city           = e.city;
        r.state          = e.state;
        r.country        = e.country;
        r.preferredUnits    = e.preferredUnits;
        r.preferredTempUnit = e.preferredTempUnit;
        r.interests         = e.getInterestList();
        return r;
    }


    /** Synchronous profile fetch for use in ViewModels on background thread */
    public RunnerProfileEntity getProfileSync() {
        return mDao.getProfileSync();
    }

    /** Store auth token locally after login */
    public void saveAuthToken(String profileId, String token) {
        mExecutor.execute(() -> mDao.updateAuthToken(profileId, token));
    }
}
