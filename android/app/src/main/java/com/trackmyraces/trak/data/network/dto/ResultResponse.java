package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;

import java.util.List;

public class ResultResponse {
    @SerializedName("id")                 public String  id;
    @SerializedName("claimId")            public String  claimId;
    @SerializedName("raceEventId")        public String  raceEventId;
    @SerializedName("raceName")           public String  raceName;
    @SerializedName("raceNameCanonical")  public String  raceNameCanonical;
    @SerializedName("raceDate")           public String  raceDate;
    @SerializedName("raceCity")           public String  raceCity;
    @SerializedName("raceState")          public String  raceState;
    @SerializedName("raceCountry")        public String  raceCountry;
    @SerializedName("distanceLabel")      public String  distanceLabel;
    @SerializedName("distanceCanonical")  public String  distanceCanonical;
    @SerializedName("distanceMeters")     public double  distanceMeters;
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
    @SerializedName("isPR")              public boolean isPR;
    @SerializedName("isBQ")             public boolean isBQ;
    @SerializedName("bqGapSeconds")      public Integer bqGapSeconds;
    @SerializedName("bqGapDisplay")      public String  bqGapDisplay;
    @SerializedName("ageGradePercent")   public Double  ageGradePercent;
    @SerializedName("splits")           public List<SplitDto> splits;
    @SerializedName("sourceUrl")        public String  sourceUrl;
    @SerializedName("notes")            public String  notes;
    @SerializedName("recordedAt")       public String  recordedAt;
    @SerializedName("updatedAt")        public String  updatedAt;

    /** Map this DTO to a Room entity for local storage. */
    public RaceResultEntity toEntity() {
        RaceResultEntity e = new RaceResultEntity();
        e.id                = id;
        e.claimId           = claimId;
        e.raceEventId       = raceEventId;
        e.raceName          = raceName;
        e.raceNameCanonical = raceNameCanonical;
        e.raceDate          = raceDate;
        e.raceCity          = raceCity;
        e.raceState         = raceState;
        e.raceCountry       = raceCountry;
        e.distanceLabel     = distanceLabel;
        e.distanceCanonical = distanceCanonical;
        e.distanceMeters    = distanceMeters;
        e.surfaceType       = surfaceType;
        e.isCertified       = isCertified;
        e.bibNumber         = bibNumber;
        e.finishTime        = finishTime;
        e.finishSeconds     = finishSeconds;
        e.chipTime          = chipTime;
        e.chipSeconds       = chipSeconds;
        e.pacePerKmSeconds  = pacePerKmSeconds;
        e.overallPlace      = overallPlace;
        e.overallTotal      = overallTotal;
        e.gender            = gender;
        e.genderPlace       = genderPlace;
        e.genderTotal       = genderTotal;
        e.ageGroupLabel     = ageGroupLabel;
        e.ageGroupCalc      = ageGroupCalc;
        e.ageGroupPlace     = ageGroupPlace;
        e.ageGroupTotal     = ageGroupTotal;
        e.ageAtRace         = ageAtRace;
        e.isPR              = isPR;
        e.isBQ              = isBQ;
        e.bqGapSeconds      = bqGapSeconds;
        e.ageGradePercent   = ageGradePercent;
        e.sourceUrl         = sourceUrl;
        e.notes             = notes;
        e.recordedAt        = recordedAt;
        e.updatedAt         = updatedAt;
        e.status            = "active";
        e.isSynced          = true;
        return e;
    }
}
