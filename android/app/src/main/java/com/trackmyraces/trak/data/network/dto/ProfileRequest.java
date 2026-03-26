package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

public class ProfileRequest {
    @SerializedName("name")           public String name;
    @SerializedName("dateOfBirth")    public String dateOfBirth;
    @SerializedName("gender")         public String gender;
    @SerializedName("city")           public String city;
    @SerializedName("state")          public String state;
    @SerializedName("country")        public String country;
    @SerializedName("preferredUnits") public String preferredUnits;
    @SerializedName("interests")      public java.util.List<String> interests;
}
