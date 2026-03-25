package com.trackmyraces.trak.ui.results;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;
import com.trackmyraces.trak.util.TimeFormatter;

import java.util.Objects;

/**
 * RaceResultAdapter
 *
 * ListAdapter (DiffUtil) for the race history RecyclerView.
 * Used on both the History screen and the Dashboard recent results list.
 *
 * Binds a RaceResultEntity to item_race_result.xml.
 */
public class RaceResultAdapter extends ListAdapter<RaceResultEntity, RaceResultAdapter.ViewHolder> {

    public interface OnResultClickListener {
        void onResultClick(RaceResultEntity result);
    }

    private final OnResultClickListener mListener;
    private boolean mUseMetric = true;

    public RaceResultAdapter(OnResultClickListener listener) {
        super(DIFF_CALLBACK);
        this.mListener = listener;
    }

    public void setUseMetric(boolean useMetric) {
        this.mUseMetric = useMetric;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_race_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RaceResultEntity result = getItem(position);
        holder.bind(result, mListener, mUseMetric);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView tvRaceName;
        final TextView tvDate;
        final TextView tvDistance;
        final TextView tvFinishTime;
        final TextView tvPace;
        final TextView tvOverallPlace;
        final TextView tvAgeGroupPlace;
        final TextView badgePR;
        final TextView badgeBQ;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRaceName      = itemView.findViewById(R.id.tv_race_name);
            tvDate          = itemView.findViewById(R.id.tv_date);
            tvDistance      = itemView.findViewById(R.id.tv_distance);
            tvFinishTime    = itemView.findViewById(R.id.tv_finish_time);
            tvPace          = itemView.findViewById(R.id.tv_pace);
            tvOverallPlace  = itemView.findViewById(R.id.tv_overall_place);
            tvAgeGroupPlace = itemView.findViewById(R.id.tv_age_group_place);
            badgePR         = itemView.findViewById(R.id.badge_pr);
            badgeBQ         = itemView.findViewById(R.id.badge_bq);
        }

        void bind(RaceResultEntity r, OnResultClickListener listener, boolean useMetric) {
            // Race name
            tvRaceName.setText(r.raceName != null ? r.raceName : "");

            // Date — format YYYY-MM-DD → readable
            tvDate.setText(formatDate(r.raceDate));

            // Distance
            tvDistance.setText(r.distanceLabel != null ? r.distanceLabel : "");

            // Finish time (prefer chip time for display)
            String timeStr = r.chipTime != null ? r.chipTime : r.finishTime;
            tvFinishTime.setText(timeStr != null ? timeStr : "--:--");

            // Pace
            String pace = r.getPaceDisplay();
            if (pace != null && !useMetric) {
                // Convert to per-mile if imperial preference
                pace = r.pacePerKmSeconds != null
                    ? TimeFormatter.pacePerMile(r.pacePerKmSeconds)
                    : null;
            }
            tvPace.setText(pace != null ? pace : "");
            tvPace.setVisibility(pace != null ? View.VISIBLE : View.GONE);

            // Overall place
            if (r.overallPlace != null && r.overallTotal != null) {
                tvOverallPlace.setText(
                    itemView.getContext().getString(R.string.place_of, r.overallPlace, r.overallTotal));
                tvOverallPlace.setVisibility(View.VISIBLE);
            } else {
                tvOverallPlace.setVisibility(View.GONE);
            }

            // Age group place
            if (r.ageGroupPlace != null && r.ageGroupTotal != null && r.ageGroupLabel != null) {
                tvAgeGroupPlace.setText(r.ageGroupLabel + "  "
                    + itemView.getContext().getString(R.string.place_of, r.ageGroupPlace, r.ageGroupTotal));
                tvAgeGroupPlace.setVisibility(View.VISIBLE);
            } else {
                tvAgeGroupPlace.setVisibility(View.GONE);
            }

            // Badges
            badgePR.setVisibility(r.isPR ? View.VISIBLE : View.GONE);
            badgeBQ.setVisibility(r.isBQ ? View.VISIBLE : View.GONE);

            // Click
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onResultClick(r);
            });
        }

        private String formatDate(String isoDate) {
            if (isoDate == null || isoDate.length() < 10) return "";
            try {
                String[] parts = isoDate.split("-");
                if (parts.length < 3) return isoDate;
                String[] months = {"","Jan","Feb","Mar","Apr","May","Jun",
                                   "Jul","Aug","Sep","Oct","Nov","Dec"};
                int m = Integer.parseInt(parts[1]);
                String month = (m >= 1 && m <= 12) ? months[m] : parts[1];
                return month + " " + Integer.parseInt(parts[2]) + ", " + parts[0];
            } catch (Exception e) {
                return isoDate;
            }
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<RaceResultEntity> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<RaceResultEntity>() {

            @Override
            public boolean areItemsTheSame(@NonNull RaceResultEntity a, @NonNull RaceResultEntity b) {
                return Objects.equals(a.id, b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull RaceResultEntity a, @NonNull RaceResultEntity b) {
                return Objects.equals(a.id, b.id)
                    && Objects.equals(a.finishTime, b.finishTime)
                    && a.isPR == b.isPR
                    && a.isBQ == b.isBQ
                    && Objects.equals(a.updatedAt, b.updatedAt);
            }
        };
}
