package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;

import java.util.List;
import java.util.Map;

// ─────────────────────────────────────────────────────────────────────────────
// Extraction
// ─────────────────────────────────────────────────────────────────────────────

public class ExtractionRequest {
    @SerializedName("url")          public String url;
    @SerializedName("runnerName")   public String runnerName;
    @SerializedName("bibNumber")    public String bibNumber;
    @SerializedName("cookie")       public String cookie;        // never logged
    @SerializedName("extraContext") public String extraContext;
}

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

// ─────────────────────────────────────────────────────────────────────────────
// Claims
// ─────────────────────────────────────────────────────────────────────────────

public class ClaimRequest {
    @SerializedName("extractionId") public String              extractionId;
    @SerializedName("edits")        public Map<String, Object> edits;
}

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

// ─────────────────────────────────────────────────────────────────────────────
// Results
// ─────────────────────────────────────────────────────────────────────────────

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

public class ResultsListResponse {
    @SerializedName("total")   public int                total;
    @SerializedName("count")   public int                count;
    @SerializedName("results") public List<ResultResponse> results;

    public java.util.List<RaceResultEntity> toEntities() {
        java.util.List<RaceResultEntity> entities = new java.util.ArrayList<>();
        if (results != null) {
            for (ResultResponse r : results) entities.add(r.toEntity());
        }
        return entities;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile
// ─────────────────────────────────────────────────────────────────────────────

public class ProfileRequest {
    @SerializedName("name")           public String name;
    @SerializedName("dateOfBirth")    public String dateOfBirth;
    @SerializedName("gender")         public String gender;
    @SerializedName("city")           public String city;
    @SerializedName("state")          public String state;
    @SerializedName("country")        public String country;
    @SerializedName("preferredUnits") public String preferredUnits;
}

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

// ─────────────────────────────────────────────────────────────────────────────
// Saved Views
// ─────────────────────────────────────────────────────────────────────────────

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

public class SavedViewResponse {
    @SerializedName("id")           public String  id;
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

public class ViewsListResponse {
    @SerializedName("views") public List<SavedViewResponse> views;
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared
// ─────────────────────────────────────────────────────────────────────────────

public class SplitDto {
    @SerializedName("label")          public String  label;
    @SerializedName("distanceMeters") public double  distanceMeters;
    @SerializedName("elapsedSeconds") public int     elapsedSeconds;
    @SerializedName("splitSeconds")   public Integer splitSeconds;
    @SerializedName("splitPlace")     public Integer splitPlace;
}
