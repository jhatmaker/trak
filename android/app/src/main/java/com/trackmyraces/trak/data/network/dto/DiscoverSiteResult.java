package com.trackmyraces.trak.data.network.dto;

public class DiscoverSiteResult {
    public String  siteId;
    public String  siteName;
    public String  description;
    public boolean found;
    public String  resultsUrl;   // direct URL to runner's profile/results page on this site
    public int     resultCount;  // approximate, 0 if unknown
    public String  notes;        // e.g. "Jane Smith, age 38, Boston MA — 47 results"
}
