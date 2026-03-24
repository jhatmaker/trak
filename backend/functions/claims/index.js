'use strict';

/**
 * functions/claims/index.js
 * POST /claims         — confirm a pending extraction as a claimed result
 * DELETE /claims/{id}  — soft-delete a claim (and its result)
 */

const { v4: uuidv4 } = require('uuid');
const { wrap, parseBody, getPathParam, getRunnerId,
        errors, ok, noContent }   = require('/opt/nodejs/shared/utils/response');
const db                          = require('/opt/nodejs/shared/db/client');

// ─── POST /claims ─────────────────────────────────────────────────────────────

async function handleCreate(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const body = parseBody(event);
  const { extractionId, edits = {} } = body;

  if (!extractionId) return errors.badRequest('extractionId is required');

  // Retrieve the pending extraction
  const extraction = await db.extraction.get(extractionId);
  if (!extraction) return errors.notFound('Extraction not found or expired (24h TTL). Please re-extract.');
  if (extraction.runnerId && extraction.runnerId !== runnerId) return errors.forbidden();

  // Merge any runner corrections on top of extracted data
  const merged = { ...extraction, ...edits };

  // ── Deduplicate race event ─────────────────────────────────────────────────
  let raceEventId;
  const existingEvent = await db.raceEvents.findBySlug(merged.raceNameCanonical, merged.raceDate);
  if (existingEvent) {
    raceEventId = existingEvent.id;
  } else {
    raceEventId = uuidv4();
    await db.raceEvents.put({
      id:               raceEventId,
      nameRaw:          merged.raceName,
      nameCanonical:    merged.raceNameCanonical,
      eventDate:        merged.raceDate,
      city:             merged.raceCity,
      state:            merged.raceState,
      country:          merged.raceCountry,
      distanceLabel:    merged.distanceLabel,
      distanceMeters:   merged.distanceMeters,
      distanceCanonical:merged.distanceCanonical,
      surfaceType:      merged.surfaceType,
      isCertified:      merged.isCertified,
      sourceUrl:        merged.sourceUrl,
    });
  }

  // ── Create claim record ────────────────────────────────────────────────────
  const claimId  = uuidv4();
  const resultId = uuidv4();

  await db.claims.put(runnerId, {
    id:           claimId,
    raceEventId,
    resultId,
    status:       'confirmed',
    sourceUrl:    merged.sourceUrl,
    isManual:     merged.isManual || false,
    extractionId: extractionId,
    claimedAt:    new Date().toISOString(),
  });

  // ── Create result record ───────────────────────────────────────────────────
  const result = {
    id:               resultId,
    claimId,
    raceEventId,
    // Race identity
    raceName:         merged.raceName,
    raceNameCanonical:merged.raceNameCanonical,
    raceDate:         merged.raceDate,
    raceCity:         merged.raceCity,
    raceState:        merged.raceState,
    raceCountry:      merged.raceCountry,
    // Distance
    distanceLabel:    merged.distanceLabel,
    distanceCanonical:merged.distanceCanonical,
    distanceMeters:   merged.distanceMeters,
    surfaceType:      merged.surfaceType,
    isCertified:      merged.isCertified,
    // Timing
    bibNumber:        merged.bibNumber,
    finishTime:       merged.finishTime,
    finishSeconds:    merged.finishSeconds,
    chipTime:         merged.chipTime,
    chipSeconds:      merged.chipSeconds,
    pacePerKmSeconds: merged.pacePerKmSeconds,
    // Placement
    overallPlace:     merged.overallPlace,
    overallTotal:     merged.overallTotal,
    gender:           merged.gender,
    genderPlace:      merged.genderPlace,
    genderTotal:      merged.genderTotal,
    ageGroupLabel:    merged.ageGroupLabel,
    ageGroupCalc:     merged.ageGroupCalc,
    ageGroupPlace:    merged.ageGroupPlace,
    ageGroupTotal:    merged.ageGroupTotal,
    ageAtRace:        merged.ageAtRace,
    // Computed
    isBQ:             merged.isBQ,
    bqGapSeconds:     merged.bqGapSeconds,
    bqStandardSeconds:merged.bqStandardSeconds,
    ageGradePercent:  merged.ageGradePercent || null,
    isPR:             false, // will be recalculated below
    // Splits
    splits:           merged.splits || [],
    // Meta
    sourceUrl:        merged.sourceUrl,
    extractionNotes:  merged.extractionNotes,
    notes:            edits.notes || null,
    status:           'active',
    recordedAt:       new Date().toISOString(),
  };

  await db.results.put(runnerId, result);

  // ── Recalculate PR for this canonical distance ─────────────────────────────
  await db.results.recalculatePRs(runnerId, merged.distanceCanonical);

  // Fetch updated isPR value
  const savedResult = await db.results.get(runnerId, resultId);

  return ok({
    claimId,
    resultId,
    raceEventId,
    isPR:             savedResult?.isPR || false,
    isBQ:             result.isBQ,
    ageAtRace:        result.ageAtRace,
    ageGroupCalc:     result.ageGroupCalc,
    ageGradePercent:  result.ageGradePercent,
  }, 201);
}

// ─── DELETE /claims/{claimId} ─────────────────────────────────────────────────

async function handleDelete(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const claimId = getPathParam(event, 'claimId');
  if (!claimId) return errors.badRequest('claimId path parameter required');

  const claim = await db.claims.get(runnerId, claimId);
  if (!claim)                    return errors.notFound('Claim not found');
  if (claim.status === 'deleted') return errors.notFound('Claim already deleted');

  // Soft-delete both claim and result
  await Promise.all([
    db.claims.softDelete(runnerId, claimId),
    db.results.softDelete(runnerId, claim.resultId),
  ]);

  // Recalculate PRs after deletion
  const deletedResult = await db.results.get(runnerId, claim.resultId);
  if (deletedResult?.distanceCanonical) {
    await db.results.recalculatePRs(runnerId, deletedResult.distanceCanonical);
  }

  return noContent();
}

// ─── Router ───────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  switch (event.httpMethod) {
    case 'POST':   return handleCreate(event);
    case 'DELETE': return handleDelete(event);
    default:       return errors.badRequest(`Method ${event.httpMethod} not supported`);
  }
});
