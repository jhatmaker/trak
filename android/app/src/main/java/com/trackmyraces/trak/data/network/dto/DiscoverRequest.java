package com.trackmyraces.trak.data.network.dto;

public class DiscoverRequest {
    public String                 runnerName;
    public String                 dateOfBirth;     // YYYY-MM-DD, optional but improves matching accuracy
    public java.util.List<String> interests;       // filters which sites are searched, e.g. ["trail","ultra"]
    public java.util.List<String> excludeSiteIds;  // site IDs the user has hidden, e.g. ["nyrr","baa"]
}
