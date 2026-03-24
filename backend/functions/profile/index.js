'use strict';

/**
 * functions/profile/index.js
 * POST   /profile  — create profile (first-time setup)
 * GET    /profile  — fetch profile
 * PUT    /profile  — update profile fields
 * DELETE /profile  — soft-delete account
 */

const { v4: uuidv4 } = require('uuid');
const { wrap, parseBody, getRunnerId, errors, ok, created, noContent } =
  require('/opt/nodejs/shared/utils/response');
const db = require('/opt/nodejs/shared/db/client');

const VALID_UNITS   = ['metric', 'imperial'];
const VALID_GENDERS = ['M', 'F', 'NB', 'prefer_not_to_say'];

function validateProfile(body, isCreate = false) {
  if (isCreate) {
    if (!body.name)        throw { isValidationError: true, message: 'name is required' };
    if (!body.dateOfBirth) throw { isValidationError: true, message: 'dateOfBirth is required (YYYY-MM-DD)' };
    if (!/^\d{4}-\d{2}-\d{2}$/.test(body.dateOfBirth))
      throw { isValidationError: true, message: 'dateOfBirth must be YYYY-MM-DD' };
    const dob = new Date(body.dateOfBirth);
    if (isNaN(dob) || dob > new Date())
      throw { isValidationError: true, message: 'dateOfBirth must be a valid past date' };
  }
  if (body.gender && !VALID_GENDERS.includes(body.gender))
    throw { isValidationError: true, message: `gender must be one of: ${VALID_GENDERS.join(', ')}` };
  if (body.preferredUnits && !VALID_UNITS.includes(body.preferredUnits))
    throw { isValidationError: true, message: `preferredUnits must be metric or imperial` };
}

// ─── POST /profile ────────────────────────────────────────────────────────────

async function handleCreate(event) {
  // On create, runnerId comes from the JWT subject claim
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const body = parseBody(event);
  validateProfile(body, true);

  // Check if profile already exists
  const existing = await db.runner.get(runnerId);
  if (existing && existing.status !== 'deleted') {
    return errors.conflict('Profile already exists — use PUT /profile to update');
  }

  const profile = {
    id:             runnerId,
    name:           body.name.trim(),
    dateOfBirth:    body.dateOfBirth,
    gender:         body.gender         || null,
    city:           body.city           || null,
    state:          body.state          || null,
    country:        body.country        || null,
    preferredUnits: body.preferredUnits || 'metric',
    status:         'active',
  };

  await db.runner.put(profile);
  return created(profile);
}

// ─── GET /profile ─────────────────────────────────────────────────────────────

async function handleGet(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const profile = await db.runner.get(runnerId);
  if (!profile || profile.status === 'deleted') return errors.notFound('Profile not found');

  // Strip internal DynamoDB keys
  const { PK, SK, GSI1PK, GSI1SK, GSI2PK, GSI2SK, entityType, ...safe } = profile;
  return ok(safe);
}

// ─── PUT /profile ─────────────────────────────────────────────────────────────

async function handleUpdate(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const body = parseBody(event);
  validateProfile(body, false);

  const profile = await db.runner.get(runnerId);
  if (!profile || profile.status === 'deleted') return errors.notFound('Profile not found');

  const EDITABLE = ['name', 'dateOfBirth', 'gender', 'city', 'state', 'country', 'preferredUnits'];
  const updates  = {};
  for (const field of EDITABLE) {
    if (field in body) updates[field] = body[field];
  }
  if (updates.name) updates.name = updates.name.trim();

  if (!Object.keys(updates).length) return errors.badRequest('No editable fields provided');

  await db.runner.update(runnerId, updates);

  const updated = await db.runner.get(runnerId);
  const { PK, SK, entityType, ...safe } = updated;
  return ok(safe);
}

// ─── DELETE /profile ──────────────────────────────────────────────────────────

async function handleDelete(event) {
  const runnerId = getRunnerId(event);
  if (!runnerId) return errors.unauthorized();

  const profile = await db.runner.get(runnerId);
  if (!profile || profile.status === 'deleted') return errors.notFound('Profile not found');

  await db.runner.softDelete(runnerId);
  // Note: results and claims are NOT deleted — runner can recover account
  // Full GDPR wipe endpoint is a separate operation in the backlog
  return noContent();
}

// ─── Router ───────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  switch (event.httpMethod) {
    case 'POST':   return handleCreate(event);
    case 'GET':    return handleGet(event);
    case 'PUT':    return handleUpdate(event);
    case 'DELETE': return handleDelete(event);
    default:       return errors.badRequest(`Method ${event.httpMethod} not supported`);
  }
});
