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
