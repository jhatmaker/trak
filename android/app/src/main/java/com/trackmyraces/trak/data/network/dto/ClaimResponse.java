package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

public class ClaimResponse {
    @SerializedName("claimId")          public String  claimId;
    @SerializedName("resultId")         public String  resultId;
    @SerializedName("raceEventId")      public String  raceEventId;
    @SerializedName("isPR")             public boolean isPR;
    @SerializedName("isBQ")             public boolean isBQ;
    @SerializedName("ageAtRace")        public Integer ageAtRace;
    @SerializedName("ageGroupCalc")     public String  ageGroupCalc;
    @SerializedName("ageGradePercent")  public Double  ageGradePercent;
}
