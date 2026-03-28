package com.trackmyraces.trak.data.network.dto;

/**
 * Request body for POST /enrich.
 * All fields except raceName are optional — the backend infers what it can.
 */
public class EnrichRequest {
    public String raceName;
    public String distanceLabel;
    public String raceDate;
    public String raceCity;
    public String raceState;
    public String raceCountry;
    /** Optional — when set, backend skips geocoding and uses these coordinates directly. */
    public Double lat;
    public Double lon;
}
