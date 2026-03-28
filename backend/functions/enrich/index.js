'use strict';

/**
 * functions/enrich/index.js
 * POST /enrich  — JWT required
 *
 * Fills in missing computed fields for an existing result using only the
 * data the app already has — no Claude tokens needed:
 *
 *   distanceCanonical + distanceMeters  ← normaliseDistance(distanceLabel || raceName)
 *   elevationStartMeters                ← Open-Meteo geocoding (free)
 *   temperatureCelsius + weatherCondition ← Open-Meteo historical archive (free)
 *
 * The caller (EditResultFragment) receives the inferred values, pre-fills
 * the edit form, and lets the user review + correct before saving.
 *
 * Request:  { raceName, distanceLabel?, raceDate?, raceCity?, raceState?, raceCountry? }
 * Response: { distanceLabel, distanceCanonical, distanceMeters,
 *             elevationStartMeters, temperatureCelsius, weatherCondition }
 */

const { wrap, parseBody, getRunnerId, errors, ok } = require('/opt/nodejs/shared/utils/response');
const { normaliseDistance }                         = require('/opt/nodejs/shared/utils/raceLogic');

// ─── Open-Meteo weather + elevation (same as extract Lambda) ──────────────────

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
    const place = [raceCity, raceState, raceCountry].filter(Boolean).join(', ');
    if (!place) return null;

    const geoRes  = await fetch(
      `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(place)}&count=1&language=en&format=json`
    );
    const geoJson = await geoRes.json();
    if (!geoJson.results?.length) return null;

    const { latitude, longitude, elevation } = geoJson.results[0];

    // Can only fetch historical weather if we have a past date
    let temperatureCelsius  = null;
    let weatherCondition    = null;

    if (raceDate) {
      const today = new Date().toISOString().slice(0, 10);
      // Open-Meteo archive only covers dates before ~5 days ago
      const raceDateObj = new Date(raceDate);
      const cutoff      = new Date(Date.now() - 5 * 24 * 60 * 60 * 1000);
      if (raceDateObj < cutoff) {
        const wRes  = await fetch(
          `https://archive-api.open-meteo.com/v1/archive` +
          `?latitude=${latitude}&longitude=${longitude}` +
          `&start_date=${raceDate}&end_date=${raceDate}` +
          `&daily=temperature_2m_max,temperature_2m_min,weathercode` +
          `&timezone=auto`
        );
        const wJson = await wRes.json();
        if (wJson.daily?.temperature_2m_max?.length) {
          const tMax  = wJson.daily.temperature_2m_max[0];
          const tMin  = wJson.daily.temperature_2m_min[0];
          const wCode = wJson.daily.weathercode[0];
          temperatureCelsius = Math.round(((tMax + tMin) / 2) * 10) / 10;
          weatherCondition   = WMO_CODES[wCode] ?? 'Partly cloudy';
        }
      }
    }

    return {
      elevationStartMeters: elevation != null ? Math.round(elevation) : null,
      temperatureCelsius,
      weatherCondition,
    };
  } catch (err) {
    console.warn(JSON.stringify({ type: 'ENRICH_WEATHER_FAILED', error: err.message }));
    return null;
  }
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const body = parseBody(event);
  const {
    raceName      = null,
    distanceLabel = null,
    raceDate      = null,
    raceCity      = null,
    raceState     = null,
    raceCountry   = null,
  } = body;

  // ── Distance inference ────────────────────────────────────────────────────
  // Try distanceLabel first; fall back to raceName (e.g. "2024 Boston Marathon" → marathon)
  const labelToTry = distanceLabel || raceName || '';
  const dist = normaliseDistance(labelToTry, null);
  // If distanceLabel is its own string but normalisation only found it in the race name,
  // use the original distanceLabel as the display value.
  const inferredDistanceLabel     = distanceLabel || (dist.key !== 'other' ? dist.key : null);
  const inferredDistanceCanonical = dist.key !== 'other' ? dist.key : null;
  const inferredDistanceMeters    = dist.meters > 0 ? dist.meters : null;
  // Estimated = distance was derived from race name only, not an explicit distanceLabel
  const distanceIsEstimated       = !distanceLabel && inferredDistanceCanonical !== null;

  // ── Weather + elevation ───────────────────────────────────────────────────
  const weather = (raceCity || raceState || raceCountry)
    ? await fetchRaceWeather(raceCity, raceState, raceCountry, raceDate)
    : null;

  console.log(JSON.stringify({
    type:              'ENRICH',
    runnerId,
    distanceCanonical: inferredDistanceCanonical,
    hasWeather:        !!weather,
  }));

  return ok({
    distanceLabel:        inferredDistanceLabel,
    distanceCanonical:    inferredDistanceCanonical,
    distanceMeters:       inferredDistanceMeters,
    distanceIsEstimated:  distanceIsEstimated,
    elevationStartMeters: weather?.elevationStartMeters ?? null,
    temperatureCelsius:   weather?.temperatureCelsius   ?? null,
    weatherCondition:     weather?.weatherCondition     ?? null,
  });
});
