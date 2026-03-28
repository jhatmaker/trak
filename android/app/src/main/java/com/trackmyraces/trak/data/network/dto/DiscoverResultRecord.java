package com.trackmyraces.trak.data.network.dto;

/**
 * One individual race result returned by the /discover endpoint.
 *
 * The discover endpoint now extracts a list of results per site rather than
 * simply confirming whether the runner was found. Each record has enough
 * detail to display a meaningful card on the Pending Matches screen.
 *
 * When the runner claims a result, {@link #resultsUrl} is passed to
 * AddResultFragment so the AI can do a full extraction (splits, weather,
 * elevation, age-grade, etc.).
 */
public class DiscoverResultRecord {

    /** Site-specific stable identifier — e.g. Athlinks athlete+event ID.
     *  Null if the site doesn't expose a stable ID. Used to build the
     *  deduplication key in Room so re-polling never creates duplicate rows. */
    public String resultId;

    /** Full race name as shown on the source site. */
    public String raceName;

    /** Race date in YYYY-MM-DD format, or null if unknown. */
    public String raceDate;

    /** Distance label as shown e.g. "Marathon", "50 Mile", "10K". */
    public String distanceLabel;

    /** Distance in metres — 0 if unknown. */
    public double distanceMeters;

    /** "City, State" or "City, Country" — null if unknown. Kept for backward compat. */
    public String location;

    /** Separate location fields — preferred over parsing location string when present. */
    public String raceCity;
    public String raceState;
    public String raceCountry;

    /** Bib number as a string, or null. */
    public String bibNumber;

    /** Finish time string e.g. "3:45:22" — null if unknown. */
    public String finishTime;

    /** Finish time in seconds — 0 if unknown. */
    public int finishSeconds;

    /** Overall finishing place — 0 if unknown. */
    public int overallPlace;

    /** Total finishers in the overall field — 0 if unknown. */
    public int overallTotal;

    /** Direct URL to this specific result/entry page.
     *  Used as the prefill URL when the runner claims this result. */
    public String resultsUrl;
}
