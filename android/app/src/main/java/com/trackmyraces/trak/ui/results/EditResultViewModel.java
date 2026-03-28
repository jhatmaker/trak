package com.trackmyraces.trak.ui.results;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.network.ApiClient;
import com.trackmyraces.trak.data.network.TrakApiService;
import com.trackmyraces.trak.data.network.dto.EnrichRequest;
import com.trackmyraces.trak.data.network.dto.EnrichResponse;
import com.trackmyraces.trak.data.repository.RaceResultRepository;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * EditResultViewModel — loads a result and saves user edits back to Room.
 *
 * Editable fields: location (city/state/country), bib number, surface type,
 * elevation gain, temperature, weather condition, notes.
 * Timing and placement fields are intentionally not editable here.
 */
public class EditResultViewModel extends AndroidViewModel {

    public final LiveData<RaceResultEntity>    result;
    public final LiveData<RunnerProfileEntity> profile;

    private final RaceResultRepository     mRepo;
    private final TrakApiService           mApi;
    private final MutableLiveData<Boolean>      mSaved    = new MutableLiveData<>();
    private final MutableLiveData<String>       mError    = new MutableLiveData<>();
    private final MutableLiveData<EnrichResponse> mEnriched = new MutableLiveData<>();
    private final MutableLiveData<Boolean>      mEnriching = new MutableLiveData<>(false);

    public EditResultViewModel(@NonNull Application application, String resultId) {
        super(application);
        mRepo   = new RaceResultRepository(application);
        mApi    = new ApiClient(application).getService();
        result  = mRepo.getResultById(resultId);
        profile = new RunnerProfileRepository(application).getProfile();
    }

    public LiveData<Boolean>       getSaved()     { return mSaved; }
    public LiveData<String>        getError()     { return mError; }
    public LiveData<EnrichResponse> getEnriched() { return mEnriched; }
    public LiveData<Boolean>       getEnriching() { return mEnriching; }

    public void save(RaceResultEntity updated) {
        mRepo.updateResult(updated, new RaceResultRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
                mSaved.postValue(true);
            }
            @Override
            public void onError(String message) {
                mError.postValue(message);
            }
        });
    }

    /** Call /enrich with known lat/lon — skips geocoding, goes straight to elevation+weather. */
    public void enrichAtCoords(double lat, double lon, RaceResultEntity r) {
        mEnriching.setValue(true);
        EnrichRequest req  = new EnrichRequest();
        req.raceName       = r.raceName;
        req.distanceLabel  = r.distanceLabel;
        req.raceDate       = r.raceDate;
        req.lat            = lat;
        req.lon            = lon;

        mApi.enrich(req).enqueue(new Callback<EnrichResponse>() {
            @Override
            public void onResponse(Call<EnrichResponse> call, Response<EnrichResponse> response) {
                mEnriching.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    mEnriched.postValue(response.body());
                } else {
                    mError.postValue("Location lookup failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<EnrichResponse> call, Throwable t) {
                mEnriching.postValue(false);
                mError.postValue("Location lookup failed: " + t.getMessage());
            }
        });
    }

    /** Call /enrich with current result data to infer missing fields. */
    public void enrich(RaceResultEntity r) {
        mEnriching.setValue(true);
        EnrichRequest req  = new EnrichRequest();
        req.raceName       = r.raceName;
        req.distanceLabel  = r.distanceLabel;
        req.raceDate       = r.raceDate;
        req.raceCity       = r.raceCity;
        req.raceState      = r.raceState;
        req.raceCountry    = r.raceCountry;

        mApi.enrich(req).enqueue(new Callback<EnrichResponse>() {
            @Override
            public void onResponse(Call<EnrichResponse> call, Response<EnrichResponse> response) {
                mEnriching.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    mEnriched.postValue(response.body());
                } else {
                    mError.postValue("Re-populate failed: HTTP " + response.code());
                }
            }
            @Override
            public void onFailure(Call<EnrichResponse> call, Throwable t) {
                mEnriching.postValue(false);
                mError.postValue("Re-populate failed: " + t.getMessage());
            }
        });
    }

    // ── Factory ────────────────────────────────────────────────────────────

    public static class Factory implements ViewModelProvider.Factory {
        private final Application mApp;
        private final String      mResultId;

        public Factory(Application app, String resultId) {
            mApp      = app;
            mResultId = resultId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new EditResultViewModel(mApp, mResultId);
        }
    }
}
