package com.trackmyraces.trak.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.databinding.FragmentLocationSearchBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Bottom sheet that searches Open-Meteo geocoding for a location by name.
 * User picks a result; the callback receives city, state, country, lat, lon.
 *
 * No API key required — Open-Meteo is free and public.
 */
public class LocationSearchBottomSheet extends BottomSheetDialogFragment {

    public interface OnLocationPickedListener {
        void onLocationPicked(String city, String state, String country, double lat, double lon);
    }

    private FragmentLocationSearchBinding mBinding;
    private OnLocationPickedListener      mListener;
    private final ExecutorService         mExecutor = Executors.newSingleThreadExecutor();
    private final OkHttpClient            mHttp     = new OkHttpClient();

    public void setOnLocationPickedListener(OnLocationPickedListener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentLocationSearchBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBinding.btnLocationSearch.setOnClickListener(v -> runSearch());

        mBinding.etLocationQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
    }

    private void runSearch() {
        String query = mBinding.etLocationQuery.getText() != null
            ? mBinding.etLocationQuery.getText().toString().trim() : "";
        if (query.isEmpty()) return;

        mBinding.progressLocation.setVisibility(View.VISIBLE);
        mBinding.tvLocationNoResults.setVisibility(View.GONE);
        mBinding.resultsContainer.removeAllViews();
        mBinding.btnLocationSearch.setEnabled(false);

        mExecutor.execute(() -> {
            try {
                // Open-Meteo only accepts a plain city name — strip ", State/Country" suffix
                // so "Bainbridge, GA" searches as "Bainbridge" and returns all matching cities.
                String cityName = query.contains(",")
                    ? query.substring(0, query.indexOf(',')).trim()
                    : query;

                String url = "https://geocoding-api.open-meteo.com/v1/search"
                    + "?name=" + java.net.URLEncoder.encode(cityName, "UTF-8")
                    + "&count=6&language=en&format=json";

                Request request = new Request.Builder().url(url).build();
                try (Response response = mHttp.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        showNoResults();
                        return;
                    }
                    String body  = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray results = json.optJSONArray("results");

                    if (results == null || results.length() == 0) {
                        showNoResults();
                        return;
                    }

                    // Build result items on main thread
                    requireActivity().runOnUiThread(() -> {
                        mBinding.progressLocation.setVisibility(View.GONE);
                        mBinding.btnLocationSearch.setEnabled(true);
                        for (int i = 0; i < results.length(); i++) {
                            try {
                                JSONObject r   = results.getJSONObject(i);
                                String name    = r.optString("name", "");
                                String admin1  = r.optString("admin1", "");
                                String country = r.optString("country", "");
                                double lat     = r.optDouble("latitude",  0);
                                double lon     = r.optDouble("longitude", 0);

                                String display = buildDisplayName(name, admin1, country);
                                addResultRow(display, name, admin1, country, lat, lon);
                            } catch (Exception ignored) { }
                        }
                    });
                }
            } catch (Exception e) {
                showNoResults();
            }
        });
    }

    private void showNoResults() {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            mBinding.progressLocation.setVisibility(View.GONE);
            mBinding.btnLocationSearch.setEnabled(true);
            mBinding.tvLocationNoResults.setVisibility(View.VISIBLE);
        });
    }

    private void addResultRow(String display, String city, String state, String country,
                              double lat, double lon) {
        View divider = new View(requireContext());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divider.setLayoutParams(dp);
        divider.setBackgroundColor(0xFFE0E0E0);
        mBinding.resultsContainer.addView(divider);

        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setText(display);
        tv.setTextSize(15f);
        tv.setPadding(0, dpToPx(12), 0, dpToPx(12));
        tv.setTextColor(0xFF212121);

        // Ripple on tap
        int[] attrs = { android.R.attr.selectableItemBackground };
        android.content.res.TypedArray ta = requireContext().obtainStyledAttributes(attrs);
        tv.setBackground(ta.getDrawable(0));
        ta.recycle();
        tv.setClickable(true);
        tv.setFocusable(true);

        tv.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onLocationPicked(city, state, country, lat, lon);
            }
            dismiss();
        });

        mBinding.resultsContainer.addView(tv);
    }

    private String buildDisplayName(String city, String state, String country) {
        StringBuilder sb = new StringBuilder();
        if (!city.isEmpty())    sb.append(city);
        if (!state.isEmpty())   { if (sb.length() > 0) sb.append(", "); sb.append(state); }
        if (!country.isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(country); }
        return sb.toString();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
