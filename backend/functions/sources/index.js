'use strict';

/**
 * functions/sources/index.js
 * GET  /sources        — list catalog (filter: type, region, distance, hidden)
 * GET  /sources/{id}   — get one source by UUID
 * POST /sources        — admin only — add source to catalog
 * PUT  /sources/{id}   — admin only — update source
 *
 * GET endpoints are public (no auth). POST/PUT require JWT + admin claim.
 */

const { wrap, ok, errors } = require('/opt/nodejs/shared/utils/response');
const pg                   = require('/opt/nodejs/shared/db/postgres');

// ─── GET /sources ─────────────────────────────────────────────────────────────

async function listSources(event) {
  const q = event.queryStringParameters || {};

  const conditions = ['s.is_active = true'];
  const params     = [];

  if (q.type) {
    params.push(q.type);
    conditions.push(`s.source_type = $${params.length}`);
  }
  if (q.distance) {
    params.push(q.distance);
    conditions.push(`$${params.length} = ANY(s.distance_tags)`);
  }
  if (q.region) {
    params.push(q.region);
    conditions.push(`$${params.length} = ANY(s.region_tags)`);
  }
  if (q.hidden === 'true') {
    conditions.push('s.is_hidden_default = true');
  } else if (q.hidden === 'false') {
    conditions.push('s.is_hidden_default = false');
  }

  const where = conditions.join(' AND ');
  const rows  = await pg.query(
    `SELECT id, name, base_url, source_type, scrape_strategy,
            login_required, canonical_slug, distance_tags, region_tags,
            is_hidden_default, is_active, notes, last_polled_at, created_at
     FROM race_source s
     WHERE ${where}
     ORDER BY name`,
    params
  );

  return ok({ sources: rows });
}

// ─── GET /sources/{id} ────────────────────────────────────────────────────────

async function getSource(id) {
  if (!isUuid(id)) return errors.badRequest('Invalid source id');

  const row = await pg.queryOne(
    `SELECT * FROM race_source WHERE id = $1`,
    [id]
  );

  if (!row) return errors.notFound('Source not found');
  return ok(row);
}

// ─── POST /sources (admin only) ───────────────────────────────────────────────

async function createSource(event) {
  if (!isAdmin(event)) return errors.forbidden('Admin access required');

  const body = parseBody(event);
  const { name, base_url, source_type, scrape_strategy, login_required,
          canonical_slug, distance_tags, region_tags, is_hidden_default, notes } = body;

  if (!name || !base_url) return errors.badRequest('name and base_url are required');

  const row = await pg.queryOne(
    `INSERT INTO race_source
       (name, base_url, source_type, scrape_strategy, login_required,
        canonical_slug, distance_tags, region_tags, is_hidden_default, notes)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
     RETURNING *`,
    [
      name, base_url,
      source_type    || null,
      scrape_strategy || null,
      login_required  === true,
      canonical_slug  || null,
      distance_tags   || [],
      region_tags     || [],
      is_hidden_default === true,
      notes           || null,
    ]
  );

  return ok(row, 201);
}

// ─── PUT /sources/{id} (admin only) ──────────────────────────────────────────

async function updateSource(id, event) {
  if (!isAdmin(event)) return errors.forbidden('Admin access required');
  if (!isUuid(id))     return errors.badRequest('Invalid source id');

  const body = parseBody(event);
  const allowed = ['name','base_url','source_type','scrape_strategy','login_required',
                   'canonical_slug','distance_tags','region_tags','is_hidden_default',
                   'is_active','notes'];

  const setClauses = [];
  const params     = [];

  for (const key of allowed) {
    if (key in body) {
      params.push(body[key]);
      setClauses.push(`${key} = $${params.length}`);
    }
  }

  if (setClauses.length === 0) return errors.badRequest('No updatable fields provided');

  params.push(id);
  const row = await pg.queryOne(
    `UPDATE race_source SET ${setClauses.join(', ')} WHERE id = $${params.length} RETURNING *`,
    params
  );

  if (!row) return errors.notFound('Source not found');
  return ok(row);
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const method     = event.httpMethod;
  const pathParams = event.pathParameters || {};
  const id         = pathParams.id || null;

  if (method === 'GET' && !id) return listSources(event);
  if (method === 'GET'  && id) return getSource(id);
  if (method === 'POST')       return createSource(event);
  if (method === 'PUT'  && id) return updateSource(id, event);

  return errors.notFound('Route not found');
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

function parseBody(event) {
  try { return JSON.parse(event.body || '{}'); } catch { return {}; }
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function isUuid(v) { return typeof v === 'string' && UUID_RE.test(v); }

function isAdmin(event) {
  // Admin claim is set by JwtAuthorizer on the Lambda context
  const ctx = event.requestContext?.authorizer || {};
  return ctx.isAdmin === 'true' || ctx.isAdmin === true;
}
