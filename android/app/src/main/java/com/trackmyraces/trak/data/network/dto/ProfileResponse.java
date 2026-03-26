package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

public class ProfileResponse {
    @SerializedName("id")             public String id;
    @SerializedName("name")           public String name;
    @SerializedName("email")          public String email;
    @SerializedName("dateOfBirth")    public String dateOfBirth;
    @SerializedName("gender")         public String gender;
    @SerializedName("city")           public String city;
    @SerializedName("state")          public String state;
    @SerializedName("country")        public String country;
    @SerializedName("preferredUnits") public String preferredUnits;
    @SerializedName("status")         public String status;
    @SerializedName("createdAt")      public String createdAt;
    @SerializedName("updatedAt")      public String updatedAt;
}
