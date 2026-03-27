package com.trackmyraces.trak.data.network.dto;

import java.util.List;
import java.util.Map;

public class DiscoverResponse {
    public List<DiscoverSiteResult> sites;
    /** True when no site returned a changed result count — Android skips savePendingMatches. */
    public boolean noChange;
    /** Current result count per site (siteId → count) — Android stores these for next comparison. */
    public Map<String, Integer> siteResultCounts;
}
