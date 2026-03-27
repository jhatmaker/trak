package com.trackmyraces.trak.ui.sources;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.trackmyraces.trak.data.db.entity.RunnerProfileEntity;
import com.trackmyraces.trak.data.db.entity.UserSitePrefEntity;
import com.trackmyraces.trak.data.repository.RunnerProfileRepository;
import com.trackmyraces.trak.data.repository.SourcesRepository;

import java.util.List;

public class ManageSourcesViewModel extends AndroidViewModel {

    private final SourcesRepository       mSourcesRepo;
    private final RunnerProfileRepository mProfileRepo;

    public final LiveData<List<UserSitePrefEntity>> prefs;
    public final LiveData<RunnerProfileEntity>      profile;

    public ManageSourcesViewModel(@NonNull Application application) {
        super(application);
        mSourcesRepo = new SourcesRepository(application);
        mProfileRepo = new RunnerProfileRepository(application);
        prefs   = mSourcesRepo.getAllPrefs();
        profile = mProfileRepo.getProfile();
    }

    public void setHidden(String siteId, boolean hidden) {
        mSourcesRepo.setHidden(siteId, hidden);
    }

    public void addCustomSource(String name, String url) {
        mSourcesRepo.addCustomSource(name, url);
    }

    public void deleteCustomSource(String siteId) {
        mSourcesRepo.deleteCustomSource(siteId);
    }
}
