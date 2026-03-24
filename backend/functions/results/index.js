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

// ─── Router ───────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const resultId = getPathParam(event, 'resultId');
  switch (event.httpMethod) {
    case 'GET':    return resultId ? handleGet(event) : handleList(event);
    case 'PUT':    return handleUpdate(event);
    default:       return errors.badRequest(`Method ${event.httpMethod} not supported`);
  }
});
