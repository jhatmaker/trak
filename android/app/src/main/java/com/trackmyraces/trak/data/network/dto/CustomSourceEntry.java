package com.trackmyraces.trak.data.network.dto;

/**
 * A user-added custom source passed to /discover so the backend can search it.
 * Mirrors the UserSitePrefEntity fields relevant to discovery.
 */
public class CustomSourceEntry {
    public String id;    // UUID — matches siteId in UserSitePrefEntity
    public String name;  // Display name
    public String url;   // Full URL of the results page

    public CustomSourceEntry(String id, String name, String url) {
        this.id   = id;
        this.name = name;
        this.url  = url;
    }
}
