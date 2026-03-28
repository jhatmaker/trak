'use strict';

/**
 * functions/results/index.js
 * GET  /results              — list results with filters/sort
 * GET  /results/{resultId}   — single result with splits
 * PUT  /results/{resultId}   — update notes or corrections
 */

const { wrap, parseBody, getPathParam, getQueryParam,
        getQueryInt, getRunnerId, errors, ok } = require('/opt/nodejs/shared/utils/response');
const { applyFiltersAndSort, secondsToTime }   = require('/opt/nodejs/shared/utils/raceLogic');
const db = require('/opt/nodejs/shared/db/client');

// ─── GET /results ─────────────────────────────────────────────────────────────

async function handleList(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const opts = {
    view:          getQueryParam(event, 'view',     'all'),
    distance:      getQueryParam(event, 'distance', 'all'),
    surface:       getQueryParam(event, 'surface',  'all'),
    yearFrom:      getQueryInt(event, 'yearFrom'),
    yearTo:        getQueryInt(event, 'yearTo'),
    raceNameSlug:  getQueryParam(event, 'raceNameSlug'),
    sort:          getQueryParam(event, 'sort',   'date'),
    order:         getQueryParam(event, 'order',  'desc'),
    limit:         Math.min(getQueryInt(event, 'limit', 50), 200),
  };

  const allResults = await db.results.listForRunner(runnerId);

  // Apply business-logic filters and sort
  let processed = applyFiltersAndSort(allResults, opts);

  // Paginate (simple offset — cursor pagination can be added later)
  const total = processed.length;
  processed   = processed.slice(0, opts.limit);

  // Format for response
  const formatted = processed.map(formatResult);

  return ok({ total, count: formatted.length, results: formatted });
}

// ─── GET /results/{resultId} ─────────────────────────────────────────────────

async function handleGet(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const resultId = getPathParam(event, 'resultId');
  if (!resultId) return errors.badRequest('resultId required');

  const result = await db.results.get(runnerId, resultId);
  if (!result || result.status === 'deleted') return errors.notFound('Result not found');

  return ok(formatResult(result, { includeSplits: true }));
}

// ─── PUT /results/{resultId} ─────────────────────────────────────────────────

async function handleUpdate(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const resultId = getPathParam(event, 'resultId');
  if (!resultId) return errors.badRequest('resultId required');

  const result = await db.results.get(runnerId, resultId);
  if (!result || result.status === 'deleted') return errors.notFound('Result not found');

  const body = parseBody(event);

  // Whitelist editable fields — protect computed / structural fields
  const EDITABLE = [
    'notes', 'finishTime', 'finishSeconds', 'chipTime', 'chipSeconds',
    'overallPlace', 'overallTotal', 'genderPlace', 'genderTotal',
    'ageGroupLabel', 'ageGroupPlace', 'ageGroupTotal', 'bibNumber',
    'surfaceType', 'isCertified',
  ];
  const updates = {};
  for (const field of EDITABLE) {
    if (field in body) updates[field] = body[field];
  }

  if (!Object.keys(updates).length) return errors.badRequest('No editable fields provided');

  await db.results.update(runnerId, resultId, updates);

  // If timing was corrected, recalculate PRs
  if ('finishSeconds' in updates || 'chipSeconds' in updates) {
    await db.results.recalculatePRs(runnerId, result.distanceCanonical);
  }

  const updated = await db.results.get(runnerId, resultId);
  return ok(formatResult(updated, { includeSplits: true }));
}

// ─── Format helper ────────────────────────────────────────────────────────────

function formatResult(r, { includeSplits = false } = {}) {
  const out = {
    id:               r.id,
    claimId:          r.claimId,
    raceEventId:      r.raceEventId,
    // Race
    raceName:         r.raceName,
    raceNameCanonical:r.raceNameCanonical,
    raceDate:         r.raceDate,
    raceCity:         r.raceCity,
    raceState:        r.raceState,
    raceCountry:      r.raceCountry,
    // Distance
    distanceLabel:    r.distanceLabel,
    distanceCanonical:r.distanceCanonical,
    distanceMeters:   r.distanceMeters,
    surfaceType:      r.surfaceType,
    isCertified:      r.isCertified,
    // Timing
    bibNumber:        r.bibNumber,
    finishTime:       r.finishTime || secondsToTime(r.finishSeconds),
    finishSeconds:    r.finishSeconds,
    chipTime:         r.chipTime   || secondsToTime(r.chipSeconds),
    chipSeconds:      r.chipSeconds,
    pacePerKmSeconds: r.pacePerKmSeconds,
    paceDisplay:      formatPace(r.pacePerKmSeconds),
    // Placement
    overallPlace:     r.overallPlace,
    overallTotal:     r.overallTotal,
    overallPct:       r.overallPlace && r.overallTotal
                        ? Math.round((1 - r.overallPlace / r.overallTotal) * 100) : null,
    gender:           r.gender,
    genderPlace:      r.genderPlace,
    genderTotal:      r.genderTotal,
    ageGroupLabel:    r.ageGroupLabel,
    ageGroupCalc:     r.ageGroupCalc,
    ageGroupPlace:    r.ageGroupPlace,
    ageGroupTotal:    r.ageGroupTotal,
    ageAtRace:        r.ageAtRace,
    // Computed
    isPR:             r.isPR || false,
    isBQ:             r.isBQ || false,
    bqGapSeconds:     r.bqGapSeconds,
    bqGapDisplay:     formatBQGap(r.bqGapSeconds),
    ageGradePercent:  r.ageGradePercent,
    // Meta
    sourceUrl:        r.sourceUrl,
    notes:            r.notes,
    recordedAt:       r.recordedAt,
    updatedAt:        r.updatedAt,
  };

  if (includeSplits) out.splits = r.splits || [];
  return out;
}

function formatPace(pacePerKmSeconds) {
  if (!pacePerKmSeconds) return null;
  const m = Math.floor(pacePerKmSeconds / 60);
  const s = pacePerKmSeconds % 60;
  return `${m}:${String(s).padStart(2, '0')}/km`;
}

function formatBQGap(bqGapSeconds) {
  if (bqGapSeconds === null || bqGapSeconds === undefined) return null;
  const abs = Math.abs(bqGapSeconds);
  const m   = Math.floor(abs / 60);
  const s   = abs % 60;
  const fmt = `${m}:${String(s).padStart(2, '0')}`;
  return bqGapSeconds >= 0 ? `${fmt} under BQ` : `${fmt} over BQ`;
}

// ─── POST /results — sync a locally-claimed result to DynamoDB ───────────────
//
// Called by the Android app to push results that were claimed offline
// (isSynced=false). The result is upserted verbatim; PR flags are
// recalculated so the backend stays consistent.

async function handlePost(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const body = parseBody(event);
  if (!body.id || !body.raceName || !body.finishSeconds) {
    return errors.badRequest('id, raceName, and finishSeconds are required');
  }

  const result = {
    id:                body.id,
    claimId:           body.claimId           ?? null,
    raceEventId:       body.raceEventId        ?? null,
    raceName:          body.raceName,
    raceNameCanonical: body.raceNameCanonical  ?? body.raceName.toLowerCase().replace(/\s+/g, '-'),
    raceDate:          body.raceDate           ?? null,
    raceCity:          body.raceCity           ?? null,
    raceState:         body.raceState          ?? null,
    raceCountry:       body.raceCountry        ?? null,
    distanceLabel:     body.distanceLabel      ?? null,
    distanceCanonical: body.distanceCanonical  ?? null,
    distanceMeters:    body.distanceMeters     ?? 0,
    surfaceType:       body.surfaceType        ?? 'road',
    isCertified:       body.isCertified        ?? null,
    bibNumber:         body.bibNumber          ?? null,
    finishTime:        body.finishTime         ?? null,
    finishSeconds:     body.finishSeconds,
    chipTime:          body.chipTime           ?? null,
    chipSeconds:       body.chipSeconds        ?? null,
    pacePerKmSeconds:  body.pacePerKmSeconds   ?? null,
    overallPlace:      body.overallPlace       ?? null,
    overallTotal:      body.overallTotal       ?? null,
    gender:            body.gender             ?? null,
    genderPlace:       body.genderPlace        ?? null,
    genderTotal:       body.genderTotal        ?? null,
    ageGroupLabel:     body.ageGroupLabel      ?? null,
    ageGroupCalc:      body.ageGroupCalc       ?? null,
    ageGroupPlace:     body.ageGroupPlace      ?? null,
    ageGroupTotal:     body.ageGroupTotal      ?? null,
    ageAtRace:         body.ageAtRace          ?? null,
    isPR:              false,  // recalculated below
    isBQ:              body.isBQ               ?? false,
    bqGapSeconds:      body.bqGapSeconds       ?? null,
    ageGradePercent:   body.ageGradePercent    ?? null,
    elevationGainMeters:  body.elevationGainMeters  ?? null,
    elevationStartMeters: body.elevationStartMeters ?? null,
    temperatureCelsius:   body.temperatureCelsius   ?? null,
    weatherCondition:     body.weatherCondition     ?? null,
    sourceUrl:         body.sourceUrl          ?? null,
    notes:             body.notes              ?? null,
    status:            'active',
    recordedAt:        body.recordedAt         ?? new Date().toISOString(),
    splits:            Array.isArray(body.splits) ? body.splits : [],
  };

  await db.results.put(runnerId, result);

  // Recalculate PR flags for this canonical distance
  if (result.distanceCanonical) {
    await db.results.recalculatePRs(runnerId, result.distanceCanonical);
  }

  // Re-fetch the now-updated record (isPR may have changed)
  const saved = await db.results.get(runnerId, result.id);
  console.log(JSON.stringify({ type: 'RESULT_SYNCED', runnerId, resultId: result.id }));
  return ok(formatResult(saved));
}

// ─── Router ───────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const resultId = getPathParam(event, 'resultId');
  switch (event.httpMethod) {
    case 'GET':    return resultId ? handleGet(event) : handleList(event);
    case 'POST':   return handlePost(event);
    case 'PUT':    return handleUpdate(event);
    default:       return errors.badRequest(`Method ${event.httpMethod} not supported`);
  }
});
