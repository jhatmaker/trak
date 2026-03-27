package com.trackmyraces.trak.data.network.dto;

public class DiscoverSiteResult {
    public String  siteId;
    public String  siteName;
    public String  description;
    public boolean found;
    /** Direct URL to the runner's athlete/profile page on this site (used as fallback). */
    public String  resultsUrl;
    /** Approximate total result count — kept for logging/notification text only. */
    public int     resultCount;
    public String  notes;
    /** Individual race results extracted during discovery.
     *  Null or empty for sites that only confirm existence without listing results. */
    public java.util.List<DiscoverResultRecord> results;
}
