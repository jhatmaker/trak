package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ExtractionResponse {
    @SerializedName("found")              public boolean found;
    @SerializedName("extractionId")       public String  extractionId;
    @SerializedName("raceName")           public String  raceName;
    @SerializedName("raceDate")           public String  raceDate;
    @SerializedName("raceCity")           public String  raceCity;
    @SerializedName("raceState")          public String  raceState;
    @SerializedName("raceCountry")        public String  raceCountry;
    @SerializedName("distanceLabel")      public String  distanceLabel;
    @SerializedName("distanceCanonical")  public String  distanceCanonical;
    @SerializedName("distanceMeters")     public Double  distanceMeters;
    @SerializedName("surfaceType")        public String  surfaceType;
    @SerializedName("isCertified")        public Boolean isCertified;
    @SerializedName("bibNumber")          public String  bibNumber;
    @SerializedName("finishTime")         public String  finishTime;
    @SerializedName("finishSeconds")      public int     finishSeconds;
    @SerializedName("chipTime")           public String  chipTime;
    @SerializedName("chipSeconds")        public Integer chipSeconds;
    @SerializedName("pacePerKmSeconds")   public Integer pacePerKmSeconds;
    @SerializedName("overallPlace")       public Integer overallPlace;
    @SerializedName("overallTotal")       public Integer overallTotal;
    @SerializedName("gender")             public String  gender;
    @SerializedName("genderPlace")        public Integer genderPlace;
    @SerializedName("genderTotal")        public Integer genderTotal;
    @SerializedName("ageGroupLabel")      public String  ageGroupLabel;
    @SerializedName("ageGroupCalc")       public String  ageGroupCalc;
    @SerializedName("ageGroupPlace")      public Integer ageGroupPlace;
    @SerializedName("ageGroupTotal")      public Integer ageGroupTotal;
    @SerializedName("ageAtRace")          public Integer ageAtRace;
    @SerializedName("isBQ")              public boolean isBQ;
    @SerializedName("bqGapSeconds")       public Integer bqGapSeconds;
    @SerializedName("ageGradePercent")    public Double  ageGradePercent;
    @SerializedName("splits")            public List<SplitDto> splits;
    @SerializedName("sourceUrl")         public String  sourceUrl;
    @SerializedName("extractionNotes")   public String  extractionNotes;
    @SerializedName("message")           public String  message;  // "not found" reason
}
