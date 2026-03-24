package com.trackmyraces.trak.ui.results;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.data.repository.RaceResultRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResultsViewModel
 *
 * Powers the History screen — a filterable, sortable list of all results.
 *
 * Filter state is held in MutableLiveData objects. A MediatorLiveData
 * combines the raw Room results with the active filters to produce
 * the displayed list. All filtering happens in-memory (no extra DB queries).
 *
 * Supported filters:
 *   distanceFilter — canonical distance key or "all"
 *   surfaceFilter  — "road", "trail", etc. or "all"
 *   yearFilter     — specific year integer or 0 for all
 *   prOnlyFilter   — show only PR results
 *   sortBy         — "date", "distance", "finishTime", "overallPlace"
 *   sortAsc        — true for ascending
 */
public class ResultsViewModel extends AndroidViewModel {

    private final RaceResultRepository mRepo;

    // ── Filter state ──────────────────────────────────────────────────────
    public final MutableLiveData<String>  distanceFilter = new MutableLiveData<>("all");
    public final MutableLiveData<String>  surfaceFilter  = new MutableLiveData<>("all");
    public final MutableLiveData<Integer> yearFilter     = new MutableLiveData<>(0);
    public final MutableLiveData<Boolean> prOnlyFilter   = new MutableLiveData<>(false);
    public final MutableLiveData<String>  sortBy         = new MutableLiveData<>("date");
    public final MutableLiveData<Boolean> sortAsc        = new MutableLiveData<>(false);

    // ── Source data from Room ──────────────────────────────────────────────
    private final LiveData<List<RaceResultEntity>> mAllResults;

    // ── Filtered + sorted output (observed by the Fragment's RecyclerView) ─
    public final MediatorLiveData<List<RaceResultEntity>> filteredResults = new MediatorLiveData<>();

    public ResultsViewModel(@NonNull Application application) {
        super(application);
        mRepo       = new RaceResultRepository(application);
        mAllResults = mRepo.getAllResults();

        // Recompute filtered list whenever source data OR any filter changes
        filteredResults.addSource(mAllResults,       data  -> recompute());
        filteredResults.addSource(distanceFilter,    value -> recompute());
        filteredResults.addSource(surfaceFilter,     value -> recompute());
        filteredResults.addSource(yearFilter,        value -> recompute());
        filteredResults.addSource(prOnlyFilter,      value -> recompute());
        filteredResults.addSource(sortBy,            value -> recompute());
        filteredResults.addSource(sortAsc,           value -> recompute());
    }

    // ── Filter actions (called from Fragment) ─────────────────────────────

    public void setDistanceFilter(String distance) { distanceFilter.setValue(distance); }
    public void setSurfaceFilter(String surface)   { surfaceFilter.setValue(surface);   }
    public void setYearFilter(int year)            { yearFilter.setValue(year);         }
    public void setPROnlyFilter(boolean prOnly)    { prOnlyFilter.setValue(prOnly);     }
    public void setSortBy(String field)            { sortBy.setValue(field);            }
    public void toggleSortDirection()              { Boolean asc = sortAsc.getValue(); sortAsc.setValue(asc == null || !asc); }

    public void clearAllFilters() {
        distanceFilter.setValue("all");
        surfaceFilter.setValue("all");
        yearFilter.setValue(0);
        prOnlyFilter.setValue(false);
        sortBy.setValue("date");
        sortAsc.setValue(false);
    }

    public boolean hasActiveFilters() {
        return !"all".equals(distanceFilter.getValue())
            || !"all".equals(surfaceFilter.getValue())
            || (yearFilter.getValue() != null && yearFilter.getValue() != 0)
            || Boolean.TRUE.equals(prOnlyFilter.getValue());
    }

    // ── Delete ────────────────────────────────────────────────────────────

    public void deleteResult(RaceResultEntity result, RaceResultRepository.RepositoryCallback<Void> callback) {
        mRepo.deleteResult(result.id, result.claimId, callback);
    }

    // ── Private: apply all filters + sort ─────────────────────────────────

    private void recompute() {
        List<RaceResultEntity> source = mAllResults.getValue();
        if (source == null) { filteredResults.setValue(new ArrayList<>()); return; }

        String  dist    = distanceFilter.getValue();
        String  surface = surfaceFilter.getValue();
        Integer year    = yearFilter.getValue();
        boolean prOnly  = Boolean.TRUE.equals(prOnlyFilter.getValue());
        String  sort    = sortBy.getValue();
        boolean asc     = Boolean.TRUE.equals(sortAsc.getValue());

        List<RaceResultEntity> filtered = source.stream()
            .filter(r -> dist    == null || dist.equals("all")    || dist.equals(r.distanceCanonical))
            .filter(r -> surface == null || surface.equals("all") || surface.equals(r.surfaceType))
            .filter(r -> year    == null || year == 0             || r.getRaceYear() == year)
            .filter(r -> !prOnly || r.isPR)
            .collect(Collectors.toList());

        // Sort
        if (sort != null) {
            switch (sort) {
                case "date":
                    Collections.sort(filtered, (a, b) -> safeCompare(b.raceDate, a.raceDate));
                    break;
                case "distance":
                    Collections.sort(filtered, (a, b) -> Double.compare(b.distanceMeters, a.distanceMeters));
                    break;
                case "finishTime":
                    Collections.sort(filtered, (a, b) -> Integer.compare(a.getBestSeconds(), b.getBestSeconds()));
                    break;
                case "overallPlace":
                    Collections.sort(filtered, (a, b) -> {
                        int pa = a.overallPlace != null ? a.overallPlace : Integer.MAX_VALUE;
                        int pb = b.overallPlace != null ? b.overallPlace : Integer.MAX_VALUE;
                        return Integer.compare(pa, pb);
                    });
                    break;
            }
        }

        if (asc) Collections.reverse(filtered);
        filteredResults.setValue(filtered);
    }

    private int safeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }
}
