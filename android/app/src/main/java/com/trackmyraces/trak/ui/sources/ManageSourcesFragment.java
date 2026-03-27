package com.trackmyraces.trak.ui.sources;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.trackmyraces.trak.R;
import com.trackmyraces.trak.data.db.entity.UserSitePrefEntity;
import com.trackmyraces.trak.databinding.FragmentManageSourcesBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ManageSourcesFragment
 *
 * Shows the full list of discovery sources the runner can configure:
 *
 *   Default sites  — built-in sites from the backend config (Athlinks, Ultrasignup, etc.)
 *                    Toggle to hide from all future discovery runs.
 *                    "Poll once" button to run a single targeted search.
 *
 *   Custom sources — URLs the runner has added manually (e.g. their local running club).
 *                    Delete button removes them.
 *
 * Navigated to from ProfileFragment ("Manage sources" button).
 */
public class ManageSourcesFragment extends Fragment {

    // Mirrors the backend DEFAULT_SITES list. Keep in sync with defaultSites.js.
    private static final List<DefaultSite> DEFAULT_SITES = new ArrayList<>();
    static {
        DEFAULT_SITES.add(new DefaultSite("athlinks",
            "Athlinks",
            "Largest race results aggregator — road, trail, triathlon, OCR, cycling"));
        DEFAULT_SITES.add(new DefaultSite("ultrasignup",
            "Ultrasignup",
            "Ultra marathon and trail race results"));
        DEFAULT_SITES.add(new DefaultSite("runsignup",
            "RunSignup",
            "Road race results from RunSignup-hosted events across the US"));
        DEFAULT_SITES.add(new DefaultSite("nyrr",
            "New York Road Runners",
            "NYRR races including NYC Marathon, Queens 10K, and more"));
        DEFAULT_SITES.add(new DefaultSite("baa",
            "Boston Athletic Association",
            "Boston Marathon and BAA road race results"));
    }

    private FragmentManageSourcesBinding mBinding;
    private ManageSourcesViewModel       mViewModel;
    private boolean                      mShowHidden = false;

    // Track which switch belongs to which siteId so we can update without a full redraw
    private final Map<String, SwitchMaterial> mSwitchMap = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentManageSourcesBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ManageSourcesViewModel.class);

        setupShowHiddenToggle();
        setupFab();

        mViewModel.prefs.observe(getViewLifecycleOwner(), prefs -> {
            Map<String, UserSitePrefEntity> prefMap = new HashMap<>();
            if (prefs != null) {
                for (UserSitePrefEntity p : prefs) prefMap.put(p.siteId, p);
            }
            renderDefaultSites(prefMap);
            renderCustomSources(prefMap);
        });
    }

    private void setupShowHiddenToggle() {
        mBinding.switchShowHidden.setOnCheckedChangeListener((btn, checked) -> {
            mShowHidden = checked;
            List<UserSitePrefEntity> current = mViewModel.prefs.getValue();
            Map<String, UserSitePrefEntity> prefMap = new HashMap<>();
            if (current != null) for (UserSitePrefEntity p : current) prefMap.put(p.siteId, p);
            renderDefaultSites(prefMap);
            renderCustomSources(prefMap);
        });
    }

    private void setupFab() {
        mBinding.fabAddSource.setOnClickListener(v -> showAddCustomSourceDialog());
    }

    private void renderDefaultSites(Map<String, UserSitePrefEntity> prefMap) {
        mSwitchMap.clear();
        LinearLayout container = mBinding.layoutDefaultSites;
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DefaultSite site : DEFAULT_SITES) {
            UserSitePrefEntity pref = prefMap.get(site.id);
            boolean isHidden = pref != null && pref.hidden;

            // Skip hidden sites unless "Show hidden" toggle is on
            if (isHidden && !mShowHidden) continue;

            View row = inflater.inflate(R.layout.item_manage_source, container, false);
            TextView       tvName  = row.findViewById(R.id.tv_site_name);
            TextView       tvDesc  = row.findViewById(R.id.tv_site_desc);
            SwitchMaterial switch_ = row.findViewById(R.id.switch_visible);
            com.google.android.material.button.MaterialButton btnPoll =
                row.findViewById(R.id.btn_poll_once);
            com.google.android.material.button.MaterialButton btnDismissed =
                row.findViewById(R.id.btn_view_dismissed);

            tvName.setText(site.name);
            tvDesc.setText(site.description);
            tvDesc.setVisibility(View.VISIBLE);

            // Switch shows "visible" state — true = include in polls, false = hidden
            switch_.setChecked(!isHidden);
            switch_.setOnCheckedChangeListener((btn, checked) -> {
                mViewModel.setHidden(site.id, !checked);
            });

            btnPoll.setOnClickListener(v -> pollOnce(site));
            btnDismissed.setOnClickListener(v -> viewDismissed(site.id, site.name));

            mSwitchMap.put(site.id, switch_);
            container.addView(row);

            // Divider between rows (except last)
            if (DEFAULT_SITES.indexOf(site) < DEFAULT_SITES.size() - 1) {
                View divider = new View(requireContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(requireContext().getColor(android.R.color.darker_gray));
                container.addView(divider);
            }
        }
    }

    private void renderCustomSources(Map<String, UserSitePrefEntity> prefMap) {
        LinearLayout container = mBinding.layoutCustomSources;
        container.removeAllViews();

        List<UserSitePrefEntity> customs = new ArrayList<>();
        for (UserSitePrefEntity p : prefMap.values()) {
            if (p.isCustom()) {
                // Only show hidden custom sources when "Show hidden" toggle is on
                if (p.hidden && !mShowHidden) continue;
                customs.add(p);
            }
        }

        if (customs.isEmpty()) {
            mBinding.tvCustomEmpty.setVisibility(View.VISIBLE);
            return;
        }
        mBinding.tvCustomEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (UserSitePrefEntity custom : customs) {
            View row = inflater.inflate(R.layout.item_manage_source, container, false);
            TextView       tvName  = row.findViewById(R.id.tv_site_name);
            TextView       tvDesc  = row.findViewById(R.id.tv_site_desc);
            SwitchMaterial swVis   = row.findViewById(R.id.switch_visible);
            com.google.android.material.button.MaterialButton btnPoll =
                row.findViewById(R.id.btn_poll_once);
            com.google.android.material.button.MaterialButton btnDismissed =
                row.findViewById(R.id.btn_view_dismissed);

            tvName.setText(custom.customName);
            tvDesc.setText(custom.customUrl);
            tvDesc.setVisibility(View.VISIBLE);
            swVis.setChecked(!custom.hidden);
            swVis.setOnCheckedChangeListener((btn, checked) ->
                mViewModel.setHidden(custom.siteId, !checked));

            // Long-press to delete custom source
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.sources_delete_custom_title))
                    .setMessage(getString(R.string.sources_delete_custom_message, custom.customName))
                    .setPositiveButton(getString(R.string.delete),
                        (d, w) -> mViewModel.deleteCustomSource(custom.siteId))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
                return true;
            });

            btnPoll.setOnClickListener(v -> pollOnceCustom(custom));
            btnDismissed.setOnClickListener(v ->
                viewDismissed(custom.siteId, custom.customName));
            container.addView(row);
        }
    }

    private void pollOnce(DefaultSite site) {
        // Navigate to DiscoverFragment excluding every default site EXCEPT this one.
        // This gives the user a focused result for just the site they tapped.
        com.trackmyraces.trak.data.db.entity.RunnerProfileEntity profile =
            mViewModel.profile.getValue();
        if (profile == null || profile.name == null) {
            android.widget.Toast.makeText(requireContext(),
                getString(R.string.sources_no_profile), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // Build exclude list: all default site IDs except the one being polled
        java.util.List<String> excludeIds = new java.util.ArrayList<>();
        for (DefaultSite s : DEFAULT_SITES) {
            if (!s.id.equals(site.id)) excludeIds.add(s.id);
        }
        Bundle args = new Bundle();
        args.putString("runnerName",     profile.name);
        args.putString("dateOfBirth",    profile.dateOfBirth != null ? profile.dateOfBirth : "");
        args.putString("interests",      profile.interests   != null ? profile.interests   : "");
        args.putString("excludeSiteIds", android.text.TextUtils.join(",", excludeIds));
        Navigation.findNavController(requireView())
            .navigate(R.id.action_manage_sources_to_discover, args);
    }

    private void viewDismissed(String siteId, String siteName) {
        Bundle args = new Bundle();
        args.putString("siteId",   siteId);
        args.putString("siteName", siteName != null ? siteName : "");
        Navigation.findNavController(requireView())
            .navigate(R.id.action_manage_sources_to_dismissed, args);
    }

    private void pollOnceCustom(UserSitePrefEntity custom) {
        // For a custom source, navigate to AddResultFragment with the URL pre-filled
        Bundle args = new Bundle();
        args.putString("prefillUrl",    custom.customUrl);
        args.putString("prefillSource", custom.customName);
        Navigation.findNavController(requireView())
            .navigate(R.id.action_manage_sources_to_add, args);
    }

    private void showAddCustomSourceDialog() {
        // Inflate a simple two-field dialog
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_custom_source, null);

        EditText etName = dialogView.findViewById(R.id.et_source_name);
        EditText etUrl  = dialogView.findViewById(R.id.et_source_url);

        new AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sources_add_custom_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save), (d, w) -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                String url  = etUrl.getText()  != null ? etUrl.getText().toString().trim()  : "";
                if (name.isEmpty() || url.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.sources_add_custom_required),
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                // Check if the URL matches a known default site
                String matchedId = matchKnownSite(url);
                if (matchedId != null) {
                    String matchedName = getDefaultSiteName(matchedId);
                    new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.sources_dedup_title))
                        .setMessage(getString(R.string.sources_dedup_message, matchedName))
                        .setPositiveButton(getString(R.string.sources_dedup_use_existing), (d2, w2) -> {
                            // Just un-hide the existing default site
                            mViewModel.setHidden(matchedId, false);
                        })
                        .setNegativeButton(getString(R.string.sources_dedup_add_anyway), (d2, w2) -> {
                            mViewModel.addCustomSource(name, url);
                        })
                        .show();
                } else {
                    mViewModel.addCustomSource(name, url);
                }
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    /**
     * If the entered URL contains a domain matching one of our known default sites,
     * return that site's id. Returns null if no match.
     */
    @Nullable
    private String matchKnownSite(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.contains("athlinks.com"))    return "athlinks";
        if (lower.contains("ultrasignup.com")) return "ultrasignup";
        if (lower.contains("runsignup.com"))   return "runsignup";
        if (lower.contains("nyrr.org"))        return "nyrr";
        if (lower.contains("baa.org"))         return "baa";
        return null;
    }

    private String getDefaultSiteName(String siteId) {
        for (DefaultSite s : DEFAULT_SITES) {
            if (s.id.equals(siteId)) return s.name;
        }
        return siteId;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    // ── Simple data class for the static default-sites list ───────────────────

    static class DefaultSite {
        final String id;
        final String name;
        final String description;
        DefaultSite(String id, String name, String description) {
            this.id          = id;
            this.name        = name;
            this.description = description;
        }
    }
}
