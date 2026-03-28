package com.trackmyraces.trak.ui.results;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;

/**
 * EditResultViewModel — loads a result and saves user edits back to Room.
 *
 * Editable fields: location (city/state/country), bib number, surface type,
 * elevation gain, temperature, weather condition, notes.
 * Timing and placement fields are intentionally not editable here.
 */
public class EditResultViewModel extends AndroidViewModel {

    public final LiveData<RaceResultEntity> result;

    private final RaceResultRepository mRepo;
    private final MutableLiveData<Boolean> mSaved = new MutableLiveData<>();
    private final MutableLiveData<String>  mError  = new MutableLiveData<>();

    public EditResultViewModel(@NonNull Application application, String resultId) {
        super(application);
        mRepo  = new RaceResultRepository(application);
        result = mRepo.getResultById(resultId);
    }

    public LiveData<Boolean> getSaved() { return mSaved; }
    public LiveData<String>  getError()  { return mError; }

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
