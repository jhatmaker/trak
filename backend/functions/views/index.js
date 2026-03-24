'use strict';

/**
 * functions/views/index.js
 * GET /views              — list saved views
 * PUT /views/{viewId}     — create or update a saved view
 * DELETE /views/{viewId}  — delete a saved view
 */

const { v4: uuidv4 } = require('uuid');
const { wrap, parseBody, getPathParam, getRunnerId,
        errors, ok, noContent } = require('/opt/nodejs/shared/utils/response');
const db = require('/opt/nodejs/shared/db/client');

const VALID_VIEW_TYPES = ['all','prs','byyear','byrace','bycanonical','bq','agegrade','custom'];
const VALID_SORTS      = ['date','distance','finishTime','overallPlace','ageGrade'];
const VALID_SURFACES   = ['road','trail','track','xc','mixed','all'];

function validateView(body) {
  if (!body.name || typeof body.name !== 'string')
    throw { isValidationError: true, message: 'name is required' };
  if (body.viewType && !VALID_VIEW_TYPES.includes(body.viewType))
    throw { isValidationError: true, message: `viewType must be one of: ${VALID_VIEW_TYPES.join(', ')}` };
  if (body.sort && !VALID_SORTS.includes(body.sort))
    throw { isValidationError: true, message: `sort must be one of: ${VALID_SORTS.join(', ')}` };
  if (body.surface && !VALID_SURFACES.includes(body.surface))
    throw { isValidationError: true, message: `surface must be one of: ${VALID_SURFACES.join(', ')}` };
}

// ─── GET /views ───────────────────────────────────────────────────────────────

async function handleList(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const viewsList = await db.views.list(runnerId);
  const clean = viewsList.map(({ PK, SK, entityType, ...v }) => v);
  return ok({ views: clean });
}

// ─── PUT /views/{viewId} ──────────────────────────────────────────────────────

async function handleUpsert(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const body   = parseBody(event);
  const viewId = getPathParam(event, 'viewId') || uuidv4();
  validateView(body);

  const view = {
    id:           viewId,
    runnerId,
    name:         body.name.trim(),
    viewType:     body.viewType     || 'custom',
    distance:     body.distance     || 'all',
    surface:      body.surface      || 'all',
    yearFrom:     body.yearFrom     || null,
    yearTo:       body.yearTo       || null,
    raceNameSlug: body.raceNameSlug || null,
    sort:         body.sort         || 'date',
    order:        body.order        || 'desc',
    status:       'active',
  };

  await db.views.put(runnerId, view);
  return ok(view);
}

// ─── DELETE /views/{viewId} ───────────────────────────────────────────────────

async function handleDelete(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const viewId = getPathParam(event, 'viewId');
  if (!viewId) return errors.badRequest('viewId required');

  await db.views.softDelete(runnerId, viewId);
  return noContent();
}

// ─── Router ───────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const viewId = getPathParam(event, 'viewId');
  switch (event.httpMethod) {
    case 'GET':    return handleList(event);
    case 'PUT':    return handleUpsert(event);
    case 'DELETE': return handleDelete(event);
    default:       return errors.badRequest(`Method ${event.httpMethod} not supported`);
  }
});
