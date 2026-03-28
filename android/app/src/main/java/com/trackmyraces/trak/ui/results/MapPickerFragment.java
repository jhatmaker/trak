package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.databinding.FragmentMapPickerBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Full-screen map fragment for picking an exact race start location.
 *
 * The user can:
 *   1. Type a city/place name and tap Search — map pans to the best geocoding result.
 *   2. Tap anywhere on the map — a marker drops at that exact point.
 *
 * On confirm, lat/lon are stored in the Navigation back-stack entry so
 * EditResultFragment can read them via savedStateHandle.
 *
 * No Google Places API needed — geocoding uses free Open-Meteo.
 */
public class MapPickerFragment extends Fragment implements OnMapReadyCallback {

    /** Keys used to pass the result back via savedStateHandle. */
    public static final String RESULT_LAT     = "map_lat";
    public static final String RESULT_LON     = "map_lon";
    public static final String RESULT_CITY    = "map_city";
    public static final String RESULT_STATE   = "map_state";
    public static final String RESULT_COUNTRY = "map_country";

    private FragmentMapPickerBinding mBinding;
    private GoogleMap                mMap;
    private Marker                   mMarker;

    private double mPickedLat = 0;
    private double mPickedLon = 0;
    private String mPickedCity    = null;
    private String mPickedState   = null;
    private String mPickedCountry = null;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final OkHttpClient    mHttp     = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentMapPickerBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SupportMapFragment mapFrag = (SupportMapFragment)
            getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFrag != null) mapFrag.getMapAsync(this);

        mBinding.etMapSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runGeocode(mBinding.etMapSearch.getText() != null
                    ? mBinding.etMapSearch.getText().toString().trim() : "");
                hideKeyboard();
                return true;
            }
            return false;
        });

        mBinding.btnUseLocation.setOnClickListener(v -> confirmPick());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        mMap = map;

        // Default view: continental US
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(38.0, -97.0), 4f));

        mMap.setOnMapClickListener(latLng -> {
            placePinAt(latLng.latitude, latLng.longitude, null, null, null);
            // Reverse-geocode to fill location labels
            reverseGeocode(latLng.latitude, latLng.longitude);
        });
    }

    // ── Geocoding (Open-Meteo — free, no key) ────────────────────────────────

    private void runGeocode(String query) {
        if (query.isEmpty()) return;

        // Strip ", State/Country" suffix — Open-Meteo only accepts plain city names
        String cityName = query.contains(",")
            ? query.substring(0, query.indexOf(',')).trim()
            : query;

        mExecutor.execute(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search"
                    + "?name=" + java.net.URLEncoder.encode(cityName, "UTF-8")
                    + "&count=1&language=en&format=json";

                Request req = new Request.Builder().url(url).build();
                try (Response resp = mHttp.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) return;
                    JSONObject json    = new JSONObject(resp.body().string());
                    JSONArray  results = json.optJSONArray("results");
                    if (results == null || results.length() == 0) return;

                    JSONObject r     = results.getJSONObject(0);
                    double lat       = r.optDouble("latitude",  0);
                    double lon       = r.optDouble("longitude", 0);
                    String city      = r.optString("name",    "");
                    String admin1    = r.optString("admin1",  "");
                    String country   = r.optString("country", "");

                    if (lat == 0 && lon == 0) return;

                    requireActivity().runOnUiThread(() -> {
                        placePinAt(lat, lon, city, admin1, country);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(lat, lon), 12f));
                    });
                }
            } catch (Exception ignored) { }
        });
    }

    private void reverseGeocode(double lat, double lon) {
        mExecutor.execute(() -> {
            try {
                // Open-Meteo doesn't have reverse geocoding, so use Nominatim (OSM) — free, no key
                String url = "https://nominatim.openstreetmap.org/reverse"
                    + "?lat=" + lat + "&lon=" + lon
                    + "&format=json&zoom=10&addressdetails=1";

                Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Trak/1.0 (contact@trackmyraces.com)")
                    .build();

                try (Response resp = mHttp.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) return;
                    JSONObject json    = new JSONObject(resp.body().string());
                    JSONObject address = json.optJSONObject("address");
                    if (address == null) return;

                    // Extract city (try multiple fields — Nominatim varies by region)
                    String city = address.optString("city",
                        address.optString("town",
                        address.optString("village",
                        address.optString("hamlet", ""))));
                    String state   = address.optString("state", "");
                    String country = address.optString("country", "");

                    requireActivity().runOnUiThread(() ->
                        updatePinLabels(lat, lon, city, state, country));
                }
            } catch (Exception ignored) { }
        });
    }

    // ── Pin management ───────────────────────────────────────────────────────

    private void placePinAt(double lat, double lon,
                             String city, String state, String country) {
        LatLng pos = new LatLng(lat, lon);
        if (mMarker == null) {
            mMarker = mMap.addMarker(new MarkerOptions().position(pos).draggable(true));
        } else {
            mMarker.setPosition(pos);
        }

        // Wire drag end → reverse geocode the dragged position
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(@NonNull Marker m) { }
            @Override public void onMarkerDrag(@NonNull Marker m) { }
            @Override public void onMarkerDragEnd(@NonNull Marker m) {
                reverseGeocode(m.getPosition().latitude, m.getPosition().longitude);
                mPickedLat = m.getPosition().latitude;
                mPickedLon = m.getPosition().longitude;
            }
        });

        updatePinLabels(lat, lon, city, state, country);
    }

    private void updatePinLabels(double lat, double lon,
                                  String city, String state, String country) {
        mPickedLat     = lat;
        mPickedLon     = lon;
        mPickedCity    = city;
        mPickedState   = state;
        mPickedCountry = country;

        StringBuilder label = new StringBuilder();
        if (city    != null && !city.isEmpty())    label.append(city);
        if (state   != null && !state.isEmpty())   { if (label.length() > 0) label.append(", "); label.append(state); }
        if (country != null && !country.isEmpty()) { if (label.length() > 0) label.append(", "); label.append(country); }
        if (label.length() == 0) {
            label.append(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon));
        }

        mBinding.tvPinLocation.setText(label.toString());
        mBinding.btnUseLocation.setEnabled(true);
    }

    // ── Confirm ──────────────────────────────────────────────────────────────

    private void confirmPick() {
        // Store result in the previous back-stack entry's savedStateHandle
        // so EditResultFragment can observe it.
        Navigation.findNavController(requireView())
            .getPreviousBackStackEntry()
            .getSavedStateHandle()
            .set(RESULT_LAT,     mPickedLat);
        Navigation.findNavController(requireView())
            .getPreviousBackStackEntry()
            .getSavedStateHandle()
            .set(RESULT_LON,     mPickedLon);
        Navigation.findNavController(requireView())
            .getPreviousBackStackEntry()
            .getSavedStateHandle()
            .set(RESULT_CITY,    mPickedCity);
        Navigation.findNavController(requireView())
            .getPreviousBackStackEntry()
            .getSavedStateHandle()
            .set(RESULT_STATE,   mPickedState);
        Navigation.findNavController(requireView())
            .getPreviousBackStackEntry()
            .getSavedStateHandle()
            .set(RESULT_COUNTRY, mPickedCountry);

        Navigation.findNavController(requireView()).popBackStack();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void hideKeyboard() {
        View v = requireActivity().getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
