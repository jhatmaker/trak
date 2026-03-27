package com.trackmyraces.trak.data.network.dto;

public class DiscoverRequest {
    public String                 runnerName;
    public String                 dateOfBirth;     // YYYY-MM-DD, optional but improves matching accuracy
    public java.util.List<String> interests;       // filters which sites are searched, e.g. ["trail","ultra"]
    public java.util.List<String> excludeSiteIds;  // site IDs the user has hidden, e.g. ["nyrr","baa"]
    /** false → cheap existence check only (background worker); true → full result extraction (default). */
    public boolean                extractResults = true;
    /** YYYY-MM-DD — if set, only return results after this date (incremental update). Null = full history. */
    public String                 sinceDate;
    /** Last known result count per site (siteId → count). Backend uses this for free pre-check. */
    public java.util.Map<String, Integer> lastKnownCounts;
}
