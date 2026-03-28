package com.trackmyraces.trak.data.network.dto;

/**
 * Response body from POST /enrich.
 * Null fields mean the backend could not infer that value.
 */
public class EnrichResponse {
    public String  distanceLabel;
    public String  distanceCanonical;
    public Double  distanceMeters;
    public Integer elevationStartMeters;
    public Double  temperatureCelsius;
    public String  weatherCondition;
    public Boolean distanceIsEstimated;
}
