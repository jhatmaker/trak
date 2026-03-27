'use strict';

/**
 * functions/subscriptions/index.js
 * GET    /subscriptions       — list current runner's subscriptions
 * POST   /subscriptions       — add a source to runner's list
 * PUT    /subscriptions/{id}  — update subscription (toggle enabled/hidden/name)
 * DELETE /subscriptions/{id}  — remove subscription
 *
 * All routes require JWT auth. runnerId comes from the JWT authorizer context.
 *
 * Source limits (enforced on POST):
 *   Free tier:  5  active enabled sources (is_enabled=true, is_hidden=false)
 *   Pro tier:   25 active enabled sources
 *   Club tier:  unlimited
 */

const { wrap, ok, errors } = require('/opt/nodejs/shared/utils/response');
const pg                   = require('/opt/nodejs/shared/db/postgres');

const SOURCE_LIMITS = { free: 5, pro: 25, club: Infinity };

// ─── GET /subscriptions ───────────────────────────────────────────────────────

async function listSubscriptions(runnerId) {
  const rows = await pg.query(
    `SELECT sub.id, sub.runner_id, sub.source_id,
            sub.runner_name_on_site, sub.bib_override, sub.extra_context,
            sub.is_enabled, sub.is_hidden, sub.auto_poll, sub.poll_frequency,
            sub.last_polled_at, sub.created_at,
            src.name AS source_name, src.base_url, src.source_type,
            src.canonical_slug, src.distance_tags, src.region_tags,
            src.scrape_strategy, src.login_required
     FROM   runner_source_subscription sub
     JOIN   race_source src ON src.id = sub.source_id
     WHERE  sub.runner_id = $1
     ORDER  BY src.name`,
    [runnerId]
  );
  return ok({ subscriptions: rows });
}

// ─── POST /subscriptions ──────────────────────────────────────────────────────

async function createSubscription(runnerId, tier, body) {
  const { sourceId, runnerNameOnSite, bibOverride, extraContext } = body;

  if (!isUuid(sourceId)) return errors.badRequest('sourceId (UUID) is required');

  // Verify source exists and is active
  const source = await pg.queryOne(
    `SELECT id FROM race_source WHERE id = $1 AND is_active = true`,
    [sourceId]
  );
  if (!source) return errors.notFound('Source not found or inactive');

  // Check for existing subscription
  const existing = await pg.queryOne(
    `SELECT id FROM runner_source_subscription WHERE runner_id = $1 AND source_id = $2`,
    [runnerId, sourceId]
  );
  if (existing) return errors.conflict('Subscription already exists');

  // Enforce source limit before creating
  const limit = SOURCE_LIMITS[tier] ?? SOURCE_LIMITS.free;
  if (isFinite(limit)) {
    const { count } = await pg.queryOne(
      `SELECT COUNT(*) AS count
       FROM runner_source_subscription
       WHERE runner_id = $1 AND is_enabled = true AND is_hidden = false`,
      [runnerId]
    );
    if (parseInt(count, 10) >= limit) {
      return errors.forbidden(
        `Source limit reached for ${tier} tier (max ${limit} active sources)`
      );
    }
  }

  const row = await pg.queryOne(
    `INSERT INTO runner_source_subscription
       (runner_id, source_id, runner_name_on_site, bib_override, extra_context)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING *`,
    [runnerId, sourceId, runnerNameOnSite || null, bibOverride || null, extraContext || null]
  );

  return ok(row, 201);
}

// ─── PUT /subscriptions/{id} ─────────────────────────────────────────────────

async function updateSubscription(runnerId, subId, body) {
  if (!isUuid(subId)) return errors.badRequest('Invalid subscription id');

  const allowed = ['runner_name_on_site','bib_override','extra_context',
                   'is_enabled','is_hidden','auto_poll','poll_frequency'];

  const setClauses = [];
  const params     = [];

  for (const key of allowed) {
    if (key in body) {
      params.push(body[key]);
      setClauses.push(`${key} = $${params.length}`);
    }
  }

  if (setClauses.length === 0) return errors.badRequest('No updatable fields provided');

  params.push(runnerId, subId);
  const row = await pg.queryOne(
    `UPDATE runner_source_subscription
     SET    ${setClauses.join(', ')}
     WHERE  runner_id = $${params.length - 1} AND id = $${params.length}
     RETURNING *`,
    params
  );

  if (!row) return errors.notFound('Subscription not found');
  return ok(row);
}

// ─── DELETE /subscriptions/{id} ──────────────────────────────────────────────

async function deleteSubscription(runnerId, subId) {
  if (!isUuid(subId)) return errors.badRequest('Invalid subscription id');

  const row = await pg.queryOne(
    `DELETE FROM runner_source_subscription
     WHERE runner_id = $1 AND id = $2
     RETURNING id`,
    [runnerId, subId]
  );

  if (!row) return errors.notFound('Subscription not found');
  return ok({ deleted: true });
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const ctx        = event.requestContext?.authorizer || {};
  const runnerId   = ctx.runnerId || ctx.principalId;
  if (!runnerId)   return errors.unauthorized('Authentication required');

  const tier       = ctx.tier || 'free';
  const method     = event.httpMethod;
  const pathParams = event.pathParameters || {};
  const subId      = pathParams.id || null;

  let body = {};
  try { body = JSON.parse(event.body || '{}'); } catch { /* ignore */ }

  if (method === 'GET'    && !subId)  return listSubscriptions(runnerId);
  if (method === 'POST')              return createSubscription(runnerId, tier, body);
  if (method === 'PUT'    && subId)   return updateSubscription(runnerId, subId, body);
  if (method === 'DELETE' && subId)   return deleteSubscription(runnerId, subId);

  return errors.notFound('Route not found');
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function isUuid(v) { return typeof v === 'string' && UUID_RE.test(v); }

