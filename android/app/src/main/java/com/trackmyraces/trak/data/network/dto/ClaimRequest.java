package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class ClaimRequest {
    @SerializedName("extractionId") public String              extractionId;
    @SerializedName("edits")        public Map<String, Object> edits;
}
