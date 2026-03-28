package com.trackmyraces.trak.data.network;

import com.trackmyraces.trak.data.network.dto.AuthRequest;
import com.trackmyraces.trak.data.network.dto.AuthResponse;
import com.trackmyraces.trak.data.network.dto.ClaimRequest;
import com.trackmyraces.trak.data.network.dto.DiscoverRequest;
import com.trackmyraces.trak.data.network.dto.DiscoverResponse;
import com.trackmyraces.trak.data.network.dto.ClaimResponse;
import com.trackmyraces.trak.data.network.dto.ExtractionRequest;
import com.trackmyraces.trak.data.network.dto.ExtractionResponse;
import com.trackmyraces.trak.data.network.dto.ProfileRequest;
import com.trackmyraces.trak.data.network.dto.ProfileResponse;
import com.trackmyraces.trak.data.network.dto.ResultResponse;
import com.trackmyraces.trak.data.network.dto.ResultsListResponse;
import com.trackmyraces.trak.data.network.dto.SavedViewRequest;
import com.trackmyraces.trak.data.network.dto.SavedViewResponse;
import com.trackmyraces.trak.data.network.dto.ViewsListResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

/**
 * TrakApiService — Retrofit interface mapping to all Trak backend endpoints.
 *
 * Base URL: BuildConfig.API_BASE_URL (injected at build time)
 * Auth: Authorization header added by AuthInterceptor for all requests.
 */
public interface TrakApiService {

    // ── Auth ──────────────────────────────────────────────────────────────

    /** POST /auth/register — public, no auth required */
    @POST("auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    /** POST /auth/login — public, no auth required */
    @POST("auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    // ── Discovery ─────────────────────────────────────────────────────────

    /**
     * POST /discover
     * Search default running sites for a runner. Public — no auth required.
     * Long timeout — one Anthropic call covers all sites (~20-30s).
     */
    @POST("discover")
    Call<DiscoverResponse> discoverResults(@Body DiscoverRequest request);

    // ── Extraction ────────────────────────────────────────────────────────

    /**
     * POST /extract
     * AI-powered extraction of a race result from a URL.
     * Long timeout — Anthropic can take 15–20s.
     */
    @POST("extract")
    Call<ExtractionResponse> extractResult(@Body ExtractionRequest request);

    // ── Claims ────────────────────────────────────────────────────────────

    /**
     * POST /claims
     * Confirm a pending extraction as a claimed result.
     */
    @POST("claims")
    Call<ClaimResponse> confirmClaim(@Body ClaimRequest request);

    /**
     * DELETE /claims/{claimId}
     * Soft-delete a claimed result.
     */
    @DELETE("claims/{claimId}")
    Call<Void> deleteClaim(@Path("claimId") String claimId);

    // ── Results ───────────────────────────────────────────────────────────

    /**
     * GET /results
     * List results with optional filters and sort.
     * Query params: view, distance, surface, yearFrom, yearTo, raceNameSlug, sort, order, limit
     */
    @GET("results")
    Call<ResultsListResponse> getResults(@QueryMap Map<String, String> filters);

    /**
     * GET /results/{resultId}
     * Single result with full splits.
     */
    @GET("results/{resultId}")
    Call<ResultResponse> getResult(@Path("resultId") String resultId);

    /**
     * PUT /results/{resultId}
     * Update editable fields on a result (notes, corrections).
     */
    @PUT("results/{resultId}")
    Call<ResultResponse> updateResult(@Path("resultId") String resultId, @Body Map<String, Object> updates);

    // ── Profile ───────────────────────────────────────────────────────────

    /**
     * POST /profile
     * Create a new runner profile (first-time setup).
     */
    @POST("profile")
    Call<ProfileResponse> createProfile(@Body ProfileRequest request);

    /**
     * GET /profile
     * Fetch the runner's profile.
     */
    @GET("profile")
    Call<ProfileResponse> getProfile();

    /**
     * PUT /profile
     * Update profile fields.
     */
    @PUT("profile")
    Call<ProfileResponse> updateProfile(@Body ProfileRequest request);

    // ── Saved Views ───────────────────────────────────────────────────────

    /**
     * GET /views
     * List all saved view presets.
     */
    @GET("views")
    Call<ViewsListResponse> getViews();

    /**
     * PUT /views/{viewId}
     * Create or update a saved view preset.
     */
    @PUT("views/{viewId}")
    Call<SavedViewResponse> upsertView(@Path("viewId") String viewId, @Body SavedViewRequest request);

    /**
     * DELETE /views/{viewId}
     * Delete a saved view preset.
     */
    @DELETE("views/{viewId}")
    Call<Void> deleteView(@Path("viewId") String viewId);
}
