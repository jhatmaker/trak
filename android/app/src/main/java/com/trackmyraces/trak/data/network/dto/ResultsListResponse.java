package com.trackmyraces.trak.data.network.dto;

import com.google.gson.annotations.SerializedName;
import com.trackmyraces.trak.data.db.entity.RaceResultEntity;

import java.util.ArrayList;
import java.util.List;

public class ResultsListResponse {
    @SerializedName("total")   public int                total;
    @SerializedName("count")   public int                count;
    @SerializedName("results") public List<ResultResponse> results;

    public List<RaceResultEntity> toEntities() {
        List<RaceResultEntity> entities = new ArrayList<>();
        if (results != null) {
            for (ResultResponse r : results) entities.add(r.toEntity());
        }
        return entities;
    }
}
