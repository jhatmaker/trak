package com.trackmyraces.trak.ui.pending;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.PendingMatchEntity;

import java.util.Objects;

/**
 * RecyclerView adapter for the Pending Matches list.
 *
 * Each card shows:
 *   - Site name + result-count badge
 *   - Optional notes (AI confirmation or Athlinks count)
 *   - "View" button (opens resultsUrl in browser)
 *   - "Add to profile" button (triggers AI extraction)
 *   - "Not me" dismiss button
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
        private final TextView       tvResultCount;
        private final TextView       tvNotes;
        private final MaterialButton btnView;
        private final MaterialButton btnClaim;
        private final MaterialButton btnDismiss;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSiteName    = itemView.findViewById(R.id.tv_site_name);
            tvResultCount = itemView.findViewById(R.id.tv_result_count);
            tvNotes       = itemView.findViewById(R.id.tv_notes);
            btnView       = itemView.findViewById(R.id.btn_view);
            btnClaim      = itemView.findViewById(R.id.btn_claim);
            btnDismiss    = itemView.findViewById(R.id.btn_dismiss);
        }

        void bind(PendingMatchEntity match, Listener listener) {
            tvSiteName.setText(match.siteName);

            if (match.resultCount > 0) {
                tvResultCount.setText(match.resultCount + " results");
                tvResultCount.setVisibility(View.VISIBLE);
            } else {
                tvResultCount.setVisibility(View.GONE);
            }

            if (match.notes != null && !match.notes.isEmpty()) {
                tvNotes.setText(match.notes);
                tvNotes.setVisibility(View.VISIBLE);
            } else {
                tvNotes.setVisibility(View.GONE);
            }

            if (match.resultsUrl != null) {
                btnView.setVisibility(View.VISIBLE);
                btnView.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(match.resultsUrl));
                    v.getContext().startActivity(intent);
                });
            } else {
                btnView.setVisibility(View.GONE);
            }

            btnClaim.setOnClickListener(v -> listener.onClaim(match));
            btnDismiss.setOnClickListener(v -> listener.onDismiss(match));
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
                    && Objects.equals(a.notes, b.notes)
                    && a.resultCount == b.resultCount;
            }
        };
}
