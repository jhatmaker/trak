package com.trackmyraces.trak.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.trackmyraces.trak.data.db.TrakDatabase;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.data.network.dto.AuthRequest;
import com.trackmyraces.trak.data.network.dto.AuthResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthViewModel extends AndroidViewModel {

    public enum AuthState { IDLE, LOADING, SUCCESS, ERROR }

    private final MutableLiveData<AuthState> mState   = new MutableLiveData<>(AuthState.IDLE);
    private final MutableLiveData<String>    mError   = new MutableLiveData<>();

    private final TrakDatabase mDb;
    private final ApiClient    mApiClient;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        mDb        = TrakDatabase.getInstance(application);
        mApiClient = new ApiClient(application);
    }

    public LiveData<AuthState> getState() { return mState; }
    public LiveData<String>    getError() { return mError; }

    public void login(String email, String password) {
        mState.setValue(AuthState.LOADING);
        mApiClient.getService()
            .login(new AuthRequest(email.trim().toLowerCase(), password))
            .enqueue(new AuthCallback());
    }

    public void register(String email, String password) {
        mState.setValue(AuthState.LOADING);
        mApiClient.getService()
            .register(new AuthRequest(email.trim().toLowerCase(), password))
            .enqueue(new AuthCallback());
    }

    private class AuthCallback implements Callback<AuthResponse> {
        @Override
        public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
            if (response.isSuccessful() && response.body() != null) {
                AuthResponse body = response.body();
                persistToken(body.token, body.runnerId);
            } else {
                String msg = "Authentication failed";
                if (response.code() == 401 || response.code() == 400) {
                    msg = "Invalid email or password";
                } else if (response.code() == 409) {
                    msg = "An account with this email already exists";
                }
                mError.postValue(msg);
                mState.postValue(AuthState.ERROR);
            }
        }

        @Override
        public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
            mError.postValue("Network error — check your connection");
            mState.postValue(AuthState.ERROR);
        }
    }

    private void persistToken(String token, String runnerId) {
        mExecutor.execute(() -> {
            RunnerProfileEntity profile = mDb.runnerProfileDao().getProfileSync();
            if (profile != null) {
                // Existing local profile — update its token and runnerId
                profile.authToken = token;
                profile.userId    = runnerId;
                mDb.runnerProfileDao().update(profile);
            } else {
                // No local profile yet — store a minimal record so the token is persisted.
                // The profile gate in MainActivity will prompt the user to complete setup.
                RunnerProfileEntity stub = new RunnerProfileEntity();
                stub.id        = runnerId;
                stub.userId    = runnerId;
                stub.authToken = token;
                stub.status    = "active";
                stub.createdAt = java.time.Instant.now().toString();
                stub.updatedAt = java.time.Instant.now().toString();
                mDb.runnerProfileDao().insertOrReplace(stub);
            }
            mState.postValue(AuthState.SUCCESS);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdown();
    }
}
