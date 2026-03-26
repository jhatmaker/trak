package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

public class ExtractionRequest {
    @SerializedName("url")          public String url;
    @SerializedName("runnerName")   public String runnerName;
    @SerializedName("bibNumber")    public String bibNumber;
    @SerializedName("cookie")       public String cookie;        // never logged
    @SerializedName("extraContext") public String extraContext;
}
