package com.trackmyraces.trak.ui.pending;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;

import java.util.Locale;
import java.util.Objects;

/**
 * RecyclerView adapter for the Pending Matches list.
 *
 * Each card represents ONE individual race result found during discovery.
 * The card shows:
 *   - Source site name (small badge header)
 *   - Race name (primary title)
 *   - Date + distance
 *   - Location (if known)
 *   - Finish time (large) + overall place + bib number
 *   - "View raw data" button (only if rawData is stored)
 *   - "Add to profile" (claim) + "Not me" (dismiss) buttons
 */
public class PendingMatchAdapter
        extends ListAdapter<PendingMatchEntity, PendingMatchAdapter.ViewHolder> {

    public interface Listener {
        void onClaim(PendingMatchEntity match);
        void onDismiss(PendingMatchEntity match);
    }

    private final Listener mListener;

    public PendingMatchAdapter(Listener listener) {
        super(DIFF_CALLBACK);
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_pending_match, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), mListener);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView       tvSiteName;
        private final TextView       tvRaceName;
        private final TextView       tvDateDistance;
        private final TextView       tvLocation;
        private final TextView       tvFinishTime;
        private final TextView       tvPlace;
        private final TextView       tvBib;
        private final TextView       tvNotes;
        private final MaterialButton btnRawData;
        private final MaterialButton btnClaim;
        private final MaterialButton btnDismiss;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSiteName     = itemView.findViewById(R.id.tv_site_name);
            tvRaceName     = itemView.findViewById(R.id.tv_race_name);
            tvDateDistance = itemView.findViewById(R.id.tv_date_distance);
            tvLocation     = itemView.findViewById(R.id.tv_location);
            tvFinishTime   = itemView.findViewById(R.id.tv_finish_time);
            tvPlace        = itemView.findViewById(R.id.tv_place);
            tvBib          = itemView.findViewById(R.id.tv_bib);
            tvNotes        = itemView.findViewById(R.id.tv_notes);
            btnRawData     = itemView.findViewById(R.id.btn_raw_data);
            btnClaim       = itemView.findViewById(R.id.btn_claim);
            btnDismiss     = itemView.findViewById(R.id.btn_dismiss);
        }

        void bind(PendingMatchEntity match, Listener listener) {
            // Source site badge
            tvSiteName.setText(match.siteName != null ? match.siteName : "");

            // Race name — fall back to site name if blank (placeholder row)
            boolean hasDetail = match.raceName != null && !match.raceName.isEmpty();
            if (hasDetail) {
                tvRaceName.setText(match.raceName);
                tvRaceName.setVisibility(View.VISIBLE);
            } else {
                tvRaceName.setVisibility(View.GONE);
            }

            // Date + distance
            String datePart   = match.raceDate != null ? formatDate(match.raceDate) : null;
            String distPart   = match.distanceLabel;
            String dateDist   = joinNonNull(" · ", datePart, distPart);
            if (dateDist != null) {
                tvDateDistance.setText(dateDist);
                tvDateDistance.setVisibility(View.VISIBLE);
            } else {
                tvDateDistance.setVisibility(View.GONE);
            }

            // Location
            if (match.location != null && !match.location.isEmpty()) {
                tvLocation.setText(match.location);
                tvLocation.setVisibility(View.VISIBLE);
            } else {
                tvLocation.setVisibility(View.GONE);
            }

            // Finish time
            if (match.finishTime != null && !match.finishTime.isEmpty()) {
                tvFinishTime.setText(match.finishTime);
                tvFinishTime.setVisibility(View.VISIBLE);
            } else if (match.finishSeconds > 0) {
                tvFinishTime.setText(secondsToTime(match.finishSeconds));
                tvFinishTime.setVisibility(View.VISIBLE);
            } else {
                tvFinishTime.setVisibility(View.GONE);
            }

            // Overall place
            if (match.overallPlace > 0) {
                String placeText = match.overallTotal > 0
                    ? String.format(Locale.US, "%d / %d overall", match.overallPlace, match.overallTotal)
                    : String.format(Locale.US, "Place %d", match.overallPlace);
                tvPlace.setText(placeText);
                tvPlace.setVisibility(View.VISIBLE);
            } else {
                tvPlace.setVisibility(View.GONE);
            }

            // Bib
            if (match.bibNumber != null && !match.bibNumber.isEmpty()) {
                tvBib.setText("Bib " + match.bibNumber);
                tvBib.setVisibility(View.VISIBLE);
            } else {
                tvBib.setVisibility(View.GONE);
            }

            // Notes (fallback for placeholder rows)
            if (!hasDetail && match.notes != null && !match.notes.isEmpty()) {
                tvNotes.setText(match.notes);
                tvNotes.setVisibility(View.VISIBLE);
            } else {
                tvNotes.setVisibility(View.GONE);
            }

            // Raw data button — only shown when raw data was stored
            if (match.rawData != null && !match.rawData.isEmpty()) {
                btnRawData.setVisibility(View.VISIBLE);
                btnRawData.setOnClickListener(v -> showRawDataDialog(match));
            } else {
                btnRawData.setVisibility(View.GONE);
            }

            btnClaim.setOnClickListener(v -> listener.onClaim(match));
            btnDismiss.setOnClickListener(v -> listener.onDismiss(match));
        }

        private void showRawDataDialog(PendingMatchEntity match) {
            String title = match.raceName != null ? match.raceName : "Raw data";

            TextView tv = new TextView(itemView.getContext());
            tv.setText(match.rawData);
            tv.setTextSize(11f);
            int pad = (int) (12 * itemView.getContext().getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);

            ScrollView scroll = new ScrollView(itemView.getContext());
            scroll.addView(tv);

            new AlertDialog.Builder(itemView.getContext())
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static String formatDate(String isoDate) {
            // "YYYY-MM-DD" → "Apr 15, 2024"
            try {
                String[] parts = isoDate.split("-");
                if (parts.length < 3) return isoDate;
                int year  = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day   = Integer.parseInt(parts[2]);
                String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                                   "Jul","Aug","Sep","Oct","Nov","Dec"};
                String m = (month >= 1 && month <= 12) ? months[month - 1] : parts[1];
                return String.format(Locale.US, "%s %d, %d", m, day, year);
            } catch (Exception ignored) {
                return isoDate;
            }
        }

        private static String secondsToTime(int seconds) {
            int h = seconds / 3600;
            int m = (seconds % 3600) / 60;
            int s = seconds % 60;
            if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
            return String.format(Locale.US, "%d:%02d", m, s);
        }

        private static String joinNonNull(String sep, String... parts) {
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p != null && !p.isEmpty()) {
                    if (sb.length() > 0) sb.append(sep);
                    sb.append(p);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<PendingMatchEntity> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<PendingMatchEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull PendingMatchEntity a, @NonNull PendingMatchEntity b) {
                return Objects.equals(a.id, b.id);
            }
            @Override
            public boolean areContentsTheSame(@NonNull PendingMatchEntity a, @NonNull PendingMatchEntity b) {
                return Objects.equals(a.status, b.status)
                    && Objects.equals(a.raceName, b.raceName)
                    && Objects.equals(a.raceDate, b.raceDate)
                    && Objects.equals(a.finishTime, b.finishTime);
            }
        };
}
