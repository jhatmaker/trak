package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

public class SavedViewRequest {
    @SerializedName("name")         public String  name;
    @SerializedName("viewType")     public String  viewType;
    @SerializedName("distance")     public String  distance;
    @SerializedName("surface")      public String  surface;
    @SerializedName("yearFrom")     public Integer yearFrom;
    @SerializedName("yearTo")       public Integer yearTo;
    @SerializedName("raceNameSlug") public String  raceNameSlug;
    @SerializedName("sort")         public String  sort;
    @SerializedName("order")        public String  order;
}
