package com.trackmyraces.trak.ui.results;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;

/**
 * ResultDetailViewModel — provides a single RaceResultEntity by ID.
 * Uses a Factory so the resultId can be passed at construction time.
 */
public class ResultDetailViewModel extends AndroidViewModel {

    public final LiveData<RaceResultEntity> result;
    private final RaceResultRepository mRepo;

    public ResultDetailViewModel(@NonNull Application application, String resultId) {
        super(application);
        mRepo  = new RaceResultRepository(application);
        result = mRepo.getResultById(resultId);
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
            return (T) new ResultDetailViewModel(mApp, mResultId);
        }
    }
}
