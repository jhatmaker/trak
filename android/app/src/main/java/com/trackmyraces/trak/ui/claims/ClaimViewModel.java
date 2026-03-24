package com.trackmyraces.trak.ui.claims;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.trackmyraces.trak.data.network.dto.ClaimResponse;
import com.trackmyraces.trak.data.network.dto.ExtractionResponse;
import com.trackmyraces.trak.data.repository.RaceResultRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * ClaimViewModel
 *
 * Manages state for the entire Add Result flow:
 *
 *   IDLE → EXTRACTING → REVIEW → CLAIMING → SUCCESS | ERROR
 *
 * Step 1 (EXTRACTING): User enters URL + runner name → POST /extract
 * Step 2 (REVIEW):     User reviews extracted data, optionally edits fields
 * Step 3 (CLAIMING):   User taps "Claim" → POST /claims
 * Step 4 (SUCCESS):    Result saved, navigate to result detail
 */
public class ClaimViewModel extends AndroidViewModel {

    private final RaceResultRepository mRepo;

    // ── UI state ──────────────────────────────────────────────────────────
    private final MutableLiveData<ClaimState>       mState       = new MutableLiveData<>(ClaimState.IDLE);
    private final MutableLiveData<ExtractionResponse> mExtraction = new MutableLiveData<>();
    private final MutableLiveData<ClaimResponse>    mClaimResult = new MutableLiveData<>();
    private final MutableLiveData<String>           mErrorMsg    = new MutableLiveData<>();

    public final LiveData<ClaimState>         state       = mState;
    public final LiveData<ExtractionResponse> extraction  = mExtraction;
    public final LiveData<ClaimResponse>      claimResult = mClaimResult;
    public final LiveData<String>             errorMsg    = mErrorMsg;

    // Fields the user may edit on the review screen
    public final MutableLiveData<String> editFinishTime    = new MutableLiveData<>();
    public final MutableLiveData<String> editAgeGroupLabel = new MutableLiveData<>();
    public final MutableLiveData<String> editBibNumber     = new MutableLiveData<>();
    public final MutableLiveData<String> editNotes         = new MutableLiveData<>();

    public ClaimViewModel(@NonNull Application application) {
        super(application);
        mRepo = new RaceResultRepository(application);
    }

    // ── Step 1: Extract ───────────────────────────────────────────────────

    /**
     * Kick off AI extraction for a URL.
     *
     * @param url          Race results page URL
     * @param runnerName   Runner name for matching
     * @param bibNumber    Optional bib number
     * @param cookie       Optional session cookie (for credentialed sites)
     * @param extraContext Optional hint e.g. "female, age group 40-44"
     */
    public void extract(String url, String runnerName, String bibNumber,
                        String cookie, String extraContext) {
        if (url == null || url.trim().isEmpty()) {
            mErrorMsg.setValue("Please enter a URL");
            return;
        }
        if (runnerName == null || runnerName.trim().isEmpty()) {
            mErrorMsg.setValue("Please enter a runner name");
            return;
        }

        mState.setValue(ClaimState.EXTRACTING);
        mErrorMsg.setValue(null);

        mRepo.extractResult(url, runnerName, bibNumber, cookie, extraContext,
            new RaceResultRepository.RepositoryCallback<ExtractionResponse>() {
                @Override
                public void onSuccess(ExtractionResponse result) {
                    if (result.found) {
                        // Pre-populate editable fields from extraction
                        editFinishTime.postValue(result.finishTime);
                        editAgeGroupLabel.postValue(result.ageGroupLabel);
                        editBibNumber.postValue(result.bibNumber);
                        mExtraction.postValue(result);
                        mState.postValue(ClaimState.REVIEW);
                    } else {
                        String msg = result.message != null
                            ? result.message
                            : "Runner not found on this page. Try adding a bib number or extra context.";
                        mErrorMsg.postValue(msg);
                        mState.postValue(ClaimState.IDLE);
                    }
                }
                @Override
                public void onError(String message) {
                    mErrorMsg.postValue("Extraction failed: " + message);
                    mState.postValue(ClaimState.ERROR);
                }
            }
        );
    }

    // ── Step 2: Review — user can edit fields, then tap claim ─────────────

    /** Build an edits map from whatever fields the user changed on the review screen. */
    private Map<String, Object> buildEdits(ExtractionResponse extracted) {
        Map<String, Object> edits = new HashMap<>();

        String ft = editFinishTime.getValue();
        if (ft != null && !ft.equals(extracted.finishTime)) edits.put("finishTime", ft);

        String ag = editAgeGroupLabel.getValue();
        if (ag != null && !ag.equals(extracted.ageGroupLabel)) edits.put("ageGroupLabel", ag);

        String bib = editBibNumber.getValue();
        if (bib != null && !bib.equals(extracted.bibNumber)) edits.put("bibNumber", bib);

        String notes = editNotes.getValue();
        if (notes != null && !notes.isEmpty()) edits.put("notes", notes);

        return edits;
    }

    // ── Step 3: Claim ─────────────────────────────────────────────────────

    public void confirmClaim() {
        ExtractionResponse extracted = mExtraction.getValue();
        if (extracted == null || extracted.extractionId == null) {
            mErrorMsg.setValue("No extraction to claim — please try again");
            return;
        }

        mState.setValue(ClaimState.CLAIMING);

        Map<String, Object> edits = buildEdits(extracted);

        mRepo.claimResult(extracted.extractionId, edits,
            new RaceResultRepository.RepositoryCallback<ClaimResponse>() {
                @Override
                public void onSuccess(ClaimResponse result) {
                    mClaimResult.postValue(result);
                    mState.postValue(ClaimState.SUCCESS);
                }
                @Override
                public void onError(String message) {
                    mErrorMsg.postValue("Claim failed: " + message);
                    mState.postValue(ClaimState.ERROR);
                }
            }
        );
    }

    // ── Step 4: Reset for another claim ───────────────────────────────────

    public void reset() {
        mState.setValue(ClaimState.IDLE);
        mExtraction.setValue(null);
        mClaimResult.setValue(null);
        mErrorMsg.setValue(null);
        editFinishTime.setValue(null);
        editAgeGroupLabel.setValue(null);
        editBibNumber.setValue(null);
        editNotes.setValue(null);
    }

    // ── State enum ────────────────────────────────────────────────────────

    public enum ClaimState {
        IDLE,        // Waiting for user to enter URL
        EXTRACTING,  // POST /extract in progress (show spinner)
        REVIEW,      // Extraction complete, showing results for review
        CLAIMING,    // POST /claims in progress
        SUCCESS,     // Claim saved — navigate to result detail
        ERROR        // Something went wrong — show error message
    }
}
