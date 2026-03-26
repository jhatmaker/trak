'use strict';

/**
 * functions/discover/index.js
 * POST /discover
 *
 * Searches a hardcoded list of popular running result websites for a given runner.
 * One Anthropic call covers all sites simultaneously (cheaper than one call per site).
 *
 * Public endpoint — no auth required (read-only, searches public data only).
 *
 * Request:  { runnerName: string, dateOfBirth?: "YYYY-MM-DD" }
 * Response: { sites: [{ siteId, siteName, found, resultsUrl, resultCount, notes }] }
 */

const { wrap, parseBody, ok, errors } = require('/opt/nodejs/shared/utils/response');
const { discoverRunner }              = require('/opt/nodejs/shared/utils/anthropic');
const { resolveSiteUrls }             = require('/opt/nodejs/shared/config/defaultSites');

// ─── Age helper ───────────────────────────────────────────────────────────────

function calcAge(dateOfBirth) {
  if (!dateOfBirth) return null;
  try {
    const dob  = new Date(dateOfBirth);
    const now  = new Date();
    let age = now.getFullYear() - dob.getFullYear();
    const bdayThisYear = new Date(now.getFullYear(), dob.getMonth(), dob.getDate());
    if (now < bdayThisYear) age--;
    return age > 0 && age < 120 ? age : null;
  } catch {
    return null;
  }
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const body = parseBody(event);

  const { runnerName, dateOfBirth = null } = body;

  if (!runnerName || typeof runnerName !== 'string' || runnerName.trim().length < 2) {
    return errors.badRequest('runnerName is required (minimum 2 characters)');
  }

  const name            = runnerName.trim();
  const approximateAge  = calcAge(dateOfBirth);
  const sites           = resolveSiteUrls(name);

  // Single Anthropic call — searches all sites simultaneously
  const rawResults = await discoverRunner({ runnerName: name, approximateAge, sites });

  // Merge site metadata back in (siteId is the join key)
  const siteMap = Object.fromEntries(sites.map(s => [s.id, s]));

  const results = (Array.isArray(rawResults) ? rawResults : []).map(r => ({
    siteId:      r.siteId,
    siteName:    siteMap[r.siteId]?.name        ?? r.siteId,
    description: siteMap[r.siteId]?.description ?? null,
    found:       !!r.found,
    resultsUrl:  r.resultsUrl  ?? null,
    resultCount: r.resultCount ?? 0,
    notes:       r.notes       ?? null,
  }));

  return ok({ sites: results });
});
