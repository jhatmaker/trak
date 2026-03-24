'use strict';

/**
 * shared/utils/response.js
 * Consistent Lambda response shapes and error handling.
 */

const CORS_HEADERS = {
  'Content-Type':                'application/json',
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers':'Content-Type,Authorization,X-Runner-Id',
};

// ─── Response builders ────────────────────────────────────────────────────────

function ok(body, statusCode = 200) {
  return {
    statusCode,
    headers: CORS_HEADERS,
    body: JSON.stringify(body),
  };
}

function created(body) {
  return ok(body, 201);
}

function noContent() {
  return { statusCode: 204, headers: CORS_HEADERS, body: '' };
}

function error(statusCode, code, message, details = null) {
  const body = { error: { code, message } };
  if (details) body.error.details = details;
  return { statusCode, headers: CORS_HEADERS, body: JSON.stringify(body) };
}

const errors = {
  badRequest:    (msg, details) => error(400, 'BAD_REQUEST',    msg, details),
  unauthorized:  (msg)         => error(401, 'UNAUTHORIZED',    msg || 'Authentication required'),
  forbidden:     (msg)         => error(403, 'FORBIDDEN',       msg || 'Access denied'),
  notFound:      (msg)         => error(404, 'NOT_FOUND',       msg || 'Resource not found'),
  conflict:      (msg)         => error(409, 'CONFLICT',        msg),
  tooMany:       (msg)         => error(429, 'TOO_MANY_REQUESTS', msg || 'Rate limit exceeded'),
  internal:      (msg)         => error(500, 'INTERNAL_ERROR',  msg || 'An internal error occurred'),
  upstream:      (msg)         => error(502, 'UPSTREAM_ERROR',  msg || 'Upstream service error'),
  timeout:       (msg)         => error(504, 'TIMEOUT',         msg || 'Request timed out'),
};

// ─── Request parsing helpers ──────────────────────────────────────────────────

function parseBody(event) {
  if (!event.body) return {};
  try {
    return typeof event.body === 'string' ? JSON.parse(event.body) : event.body;
  } catch {
    throw { isValidationError: true, message: 'Request body is not valid JSON' };
  }
}

function getPathParam(event, name) {
  return event.pathParameters?.[name] || null;
}

function getQueryParam(event, name, defaultVal = null) {
  return event.queryStringParameters?.[name] ?? defaultVal;
}

function getQueryInt(event, name, defaultVal = null) {
  const val = getQueryParam(event, name);
  if (val === null) return defaultVal;
  const n = parseInt(val, 10);
  return isNaN(n) ? defaultVal : n;
}

/**
 * Extract the authenticated runner ID from the API Gateway authoriser context.
 * The JWT authoriser sets this in event.requestContext.authorizer.
 */
function getRunnerId(event) {
  return event.requestContext?.authorizer?.runnerId || null;
}

// ─── Validation ───────────────────────────────────────────────────────────────

function require(obj, fields) {
  const missing = fields.filter(f => obj[f] === undefined || obj[f] === null || obj[f] === '');
  if (missing.length) {
    throw { isValidationError: true, message: `Missing required fields: ${missing.join(', ')}` };
  }
}

// ─── Lambda handler wrapper ───────────────────────────────────────────────────

/**
 * Wrap a Lambda handler with standard error handling, logging, and timing.
 * Usage:
 *   exports.handler = wrap(async (event) => {
 *     const body = parseBody(event);
 *     return ok({ result: 'data' });
 *   });
 */
function wrap(fn) {
  return async (event, context) => {
    const start = Date.now();
    const method = event.httpMethod || 'INVOKE';
    const path   = event.path || event.resource || '';

    // Redact sensitive fields before logging
    const safeEvent = redactEvent(event);
    console.log(JSON.stringify({ type: 'REQUEST', method, path, event: safeEvent }));

    try {
      const result = await fn(event, context);
      console.log(JSON.stringify({ type: 'RESPONSE', method, path, status: result.statusCode, ms: Date.now() - start }));
      return result;
    } catch (err) {
      if (err.isValidationError) {
        console.log(JSON.stringify({ type: 'VALIDATION_ERROR', message: err.message }));
        return errors.badRequest(err.message);
      }

      // Anthropic API errors
      if (err.status === 429) return errors.tooMany('Anthropic rate limit reached — please retry in a moment');
      if (err.status === 529) return errors.upstream('Anthropic API overloaded — please retry');
      if (err.status >= 500)  return errors.upstream(`Anthropic API error: ${err.message}`);

      // DynamoDB errors
      if (err.name === 'ProvisionedThroughputExceededException') return errors.tooMany('Database throughput exceeded');
      if (err.name === 'ConditionalCheckFailedException')        return errors.conflict('Resource was modified concurrently');

      // Generic
      console.error(JSON.stringify({ type: 'UNHANDLED_ERROR', message: err.message, stack: err.stack }));
      return errors.internal(process.env.STAGE === 'prod' ? undefined : err.message);
    }
  };
}

function redactEvent(event) {
  const safe = { ...event };
  if (safe.headers) {
    safe.headers = { ...safe.headers };
    if (safe.headers.Authorization) safe.headers.Authorization = '[REDACTED]';
    if (safe.headers.Cookie)        safe.headers.Cookie        = '[REDACTED]';
  }
  if (safe.body) {
    try {
      const body = JSON.parse(safe.body);
      if (body.cookie)   body.cookie   = '[REDACTED]';
      if (body.password) body.password = '[REDACTED]';
      safe.body = JSON.stringify(body);
    } catch { /* not JSON, leave as-is */ }
  }
  return safe;
}

module.exports = {
  ok, created, noContent, errors,
  parseBody, getPathParam, getQueryParam, getQueryInt, getRunnerId,
  require, wrap,
};
