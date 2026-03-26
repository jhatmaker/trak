'use strict';

/**
 * functions/extract/index.js
 * POST /extract
 *
 * Receives a URL + runner identity, calls Anthropic to extract the result,
 * enriches it with computed fields, stores as a pending extraction (TTL 24h),
 * and returns the structured data for runner review before claiming.
 */

const { v4: uuidv4 } = require('uuid');
const { wrap, parseBody, getRunnerId, errors, ok } = require('/opt/nodejs/shared/utils/response');
const { extractRaceResult }   = require('/opt/nodejs/shared/utils/anthropic');
const { normaliseDistance, calcAgeAtRace, calcAgeGroup,
        canonicaliseRaceName, checkBQ, timeToSeconds,
        calcPacePerKm }        = require('/opt/nodejs/shared/utils/raceLogic');
const db                       = require('/opt/nodejs/shared/db/client');
const { runner: runnerDb }     = db;

// ─── Weather fetch (Open-Meteo — free, no API key required) ──────────────────

const WMO_CODES = {
  0: 'Clear', 1: 'Clear', 2: 'Partly cloudy', 3: 'Overcast',
  45: 'Fog', 48: 'Fog',
  51: 'Drizzle', 53: 'Drizzle', 55: 'Drizzle',
  61: 'Rain', 63: 'Rain', 65: 'Rain',
  71: 'Snow', 73: 'Snow', 75: 'Snow',
  80: 'Rain', 81: 'Rain', 82: 'Rain',
  95: 'Thunderstorm', 96: 'Thunderstorm', 99: 'Thunderstorm',
};

async function fetchRaceWeather(raceCity, raceState, raceCountry, raceDate) {
  try {
    // Step 1: geocode the race location
    const place = [raceCity, raceState, raceCountry].filter(Boolean).join(', ');
    if (!place) return null;

    const geoRes = await fetch(
      `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(place)}&count=1&language=en&format=json`
    );
    const geoJson = await geoRes.json();
    if (!geoJson.results?.length) return null;

    const { latitude, longitude, elevation } = geoJson.results[0];

    // Step 2: fetch historical weather for the race date
    const weatherRes = await fetch(
      `https://archive-api.open-meteo.com/v1/archive` +
      `?latitude=${latitude}&longitude=${longitude}` +
      `&start_date=${raceDate}&end_date=${raceDate}` +
      `&daily=temperature_2m_max,temperature_2m_min,weathercode` +
      `&timezone=auto`
    );
    const weatherJson = await weatherRes.json();
    if (!weatherJson.daily?.temperature_2m_max?.length) return null;

    const tMax  = weatherJson.daily.temperature_2m_max[0];
    const tMin  = weatherJson.daily.temperature_2m_min[0];
    const wCode = weatherJson.daily.weathercode[0];

    return {
      temperatureCelsius:   Math.round(((tMax + tMin) / 2) * 10) / 10,
      weatherCondition:     WMO_CODES[wCode] ?? 'Partly cloudy',
      elevationStartMeters: elevation != null ? Math.round(elevation) : null,
    };
  } catch (err) {
    // Weather is non-critical — log and continue
    console.warn(JSON.stringify({ type: 'WEATHER_FETCH_FAILED', error: err.message }));
    return null;
  }
}

// ─── Validation ───────────────────────────────────────────────────────────────

function validateRequest(body) {
  if (!body.url || typeof body.url !== 'string') {
    throw { isValidationError: true, message: 'url is required' };
  }
  if (!body.runnerName && !body.runnerId) {
    throw { isValidationError: true, message: 'runnerName or runnerId is required' };
  }
  try { new URL(body.url); } catch {
    throw { isValidationError: true, message: 'url must be a valid URL' };
  }
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const body     = parseBody(event);
  const runnerId = getRunnerId(event);

  validateRequest(body);

  const {
    url,
    runnerName,
    bibNumber    = null,
    cookie       = null,   // session cookie for credentialed sites — never logged
    extraContext = null,
  } = body;

  // Resolve runner name from profile if not provided
  let resolvedName = runnerName;
  if (!resolvedName && runnerId) {
    const profile = await runnerDb.get(runnerId);
    if (!profile) return errors.notFound('Runner profile not found');
    resolvedName = profile.name;
  }
  if (!resolvedName) return errors.badRequest('runnerName is required');

  // ── Call Anthropic ─────────────────────────────────────────────────────────
  const raw = await extractRaceResult({
    runnerName:   resolvedName,
    bibNumber,
    url,
    cookie,
    extraContext,
  });

  if (!raw.found) {
    return ok({
      found:         false,
      extractionId:  null,
      message:       `Runner "${resolvedName}" was not found on this results page.`,
    });
  }

  // ── Enrich with computed fields ────────────────────────────────────────────

  // Normalise distance
  const { key: distanceCanonical, meters: distanceMeters } =
    normaliseDistance(raw.distance_label, raw.distance_meters);

  // Ensure finish_seconds is populated (parse from string if needed)
  const finishSeconds = raw.finish_seconds || timeToSeconds(raw.finish_time);
  const chipSeconds   = raw.chip_seconds   || timeToSeconds(raw.chip_time) || null;

  // Pace
  const pacePerKmSeconds = calcPacePerKm(chipSeconds || finishSeconds, distanceMeters);

  // Canonical race name
  const raceNameCanonical = canonicaliseRaceName(raw.race_name);

  // Race-day weather (non-blocking — failure returns null)
  const weather = (raw.race_date && (raw.race_city || raw.race_state || raw.race_country))
    ? await fetchRaceWeather(raw.race_city, raw.race_state, raw.race_country, raw.race_date)
    : null;

  // Age-related fields (requires runner DOB from profile)
  let ageAtRace = null, ageGroup = null, isBQ = false, bqGapSeconds = null, bqStandard = null;
  if (runnerId && raw.race_date) {
    const profile = await runnerDb.get(runnerId);
    if (profile?.dateOfBirth) {
      ageAtRace = calcAgeAtRace(profile.dateOfBirth, raw.race_date);
      ageGroup  = calcAgeGroup(ageAtRace);

      if (distanceCanonical === 'marathon' && finishSeconds) {
        const gender  = raw.gender || profile.gender;
        const bqCheck = checkBQ(chipSeconds || finishSeconds, ageAtRace, gender);
        isBQ          = bqCheck.isBQ;
        bqGapSeconds  = bqCheck.bqGapSeconds;
        bqStandard    = bqCheck.bqStandardSeconds;
      }
    }
  }

  // ── Build enriched extraction record ──────────────────────────────────────
  const extractionId = uuidv4();
  const enriched = {
    // Identity
    extractionId,
    runnerId:         runnerId || null,
    runnerName:       resolvedName,
    // Race details
    raceName:         raw.race_name,
    raceNameCanonical,
    raceDate:         raw.race_date,
    raceCity:         raw.race_city    || null,
    raceState:        raw.race_state   || null,
    raceCountry:      raw.race_country || null,
    // Distance
    distanceLabel:    raw.distance_label,
    distanceCanonical,
    distanceMeters:   distanceMeters || raw.distance_meters || null,
    surfaceType:      raw.surface_type || null,
    isCertified:      raw.is_certified || null,
    // Timing
    bibNumber:        raw.bib_number   || bibNumber || null,
    finishTime:       raw.finish_time,
    finishSeconds,
    chipTime:         raw.chip_time    || null,
    chipSeconds:      chipSeconds      || null,
    pacePerKmSeconds,
    // Placement
    overallPlace:     raw.overall_place || null,
    overallTotal:     raw.overall_total || null,
    gender:           raw.gender        || null,
    genderPlace:      raw.gender_place  || null,
    genderTotal:      raw.gender_total  || null,
    ageGroupLabel:    raw.age_group_label || null,
    ageGroupPlace:    raw.age_group_place || null,
    ageGroupTotal:    raw.age_group_total || null,
    // Computed
    ageAtRace,
    ageGroupCalc:     ageGroup,
    isBQ,
    bqGapSeconds,
    bqStandardSeconds: bqStandard,
    // Race conditions
    elevationGainMeters:  raw.elevation_gain_meters || null,
    elevationStartMeters: weather?.elevationStartMeters ?? null,
    temperatureCelsius:   weather?.temperatureCelsius   ?? null,
    weatherCondition:     weather?.weatherCondition     ?? raw.weather_condition ?? null,
    // Splits
    splits: (raw.splits || []).map(s => ({
      label:           s.label,
      distanceMeters:  s.distance_meters,
      elapsedSeconds:  s.elapsed_seconds,
      splitSeconds:    s.split_seconds   || null,
      splitPlace:      s.split_place     || null,
    })),
    // Meta
    sourceUrl:          raw.source_url || url,
    extractionNotes:    raw.extraction_notes || null,
    extractedAt:        new Date().toISOString(),
  };

  // Store with 24-hour TTL (runner must claim within this window)
  await db.extraction.put(extractionId, enriched);

  return ok({ found: true, ...enriched });
});
