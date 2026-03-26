package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

public class SplitDto {
    @SerializedName("label")          public String  label;
    @SerializedName("distanceMeters") public double  distanceMeters;
    @SerializedName("elapsedSeconds") public int     elapsedSeconds;
    @SerializedName("splitSeconds")   public Integer splitSeconds;
    @SerializedName("splitPlace")     public Integer splitPlace;
}
