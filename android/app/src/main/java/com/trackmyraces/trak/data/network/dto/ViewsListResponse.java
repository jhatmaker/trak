package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ViewsListResponse {
    @SerializedName("views") public List<SavedViewResponse> views;
}
