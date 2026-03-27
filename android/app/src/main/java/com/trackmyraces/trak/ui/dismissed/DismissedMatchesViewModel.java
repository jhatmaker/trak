package com.trackmyraces.trak.ui.dismissed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;

import java.util.List;

/**
 * ViewModel for DismissedMatchesFragment.
 * Shows dismissed ("not me") entries for all sites or a single site.
 * Exposes a restore action to move a dismissed entry back to pending.
 */
public class DismissedMatchesViewModel extends AndroidViewModel {

    private final RaceResultRepository mRepo;

    public final LiveData<List<PendingMatchEntity>> dismissedMatches;

    private DismissedMatchesViewModel(@NonNull Application application, @Nullable String siteId) {
        super(application);
        mRepo = new RaceResultRepository(application);
        dismissedMatches = (siteId != null && !siteId.isEmpty())
            ? mRepo.getDismissedMatchesForSite(siteId)
            : mRepo.getDismissedMatches();
    }

    /** Move a dismissed entry back to pending so the runner can claim or re-dismiss it. */
    public void restore(PendingMatchEntity match) {
        mRepo.restorePendingMatch(match.id);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ViewModelProvider.Factory factory(
            @NonNull Application application, @Nullable String siteId) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new DismissedMatchesViewModel(application, siteId);
            }
        };
    }
}
