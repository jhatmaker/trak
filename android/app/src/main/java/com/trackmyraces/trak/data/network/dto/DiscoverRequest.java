package com.trackmyraces.trak.data.network.dto;

public class DiscoverRequest {
    /** Device-local UUID — used by backend to store/compare per-user site counts in DynamoDB. */
    public String                 userId;
    /**
     * GUIDs of the sources to search. When present, the backend resolves each GUID
     * to its site config and ignores interests/excludeSiteIds.
     * e.g. ["00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002"]
     */
    public java.util.List<String> sourceIds;
    /**
     * User-added custom sources to search alongside the default sites.
     * The backend builds a site:-restricted web_search query for each.
     */
    public java.util.List<CustomSourceEntry> customSources;
    public String                 runnerName;
    public String                 dateOfBirth;     // YYYY-MM-DD, optional but improves matching accuracy
    /** false → cheap existence check only (background worker); true → full result extraction (default). */
    public boolean                extractResults = true;
    /** YYYY-MM-DD — if set, only return results after this date (incremental update). Null = full history. */
    public String                 sinceDate;
    /** Last known result count per site (siteId → count). Backend uses this for free pre-check. */
    public java.util.Map<String, Integer> lastKnownCounts;
}
