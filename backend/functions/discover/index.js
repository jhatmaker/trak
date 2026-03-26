'use strict';

/**
 * functions/discover/index.js
 * POST /discover
 *
 * Searches popular running result websites for a given runner.
 * Sites with a direct API are called directly (faster, free, no Claude tokens).
 * Sites without a direct API fall back to Claude web_search.
 *
 * Public endpoint — no auth required (read-only, searches public data only).
 *
 * Request:  { runnerName: string, dateOfBirth?: "YYYY-MM-DD", interests?: string[] }
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

// ─── Direct API caller ────────────────────────────────────────────────────────

/**
 * Call a site's direct API endpoint and return a normalised result object.
 * Each site may have its own response shape; dispatch by site.id.
 */
async function callDirectApi(site) {
  try {
    const res = await fetch(site.directApiUrl, {
      headers: { 'Accept': 'application/json', 'User-Agent': 'Trak/1.0' },
      signal:  AbortSignal.timeout(10_000),
    });

    if (!res.ok) {
      return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0,
               notes: `API returned HTTP ${res.status}` };
    }

    const data = await res.json();

    // ── Athlinks ──────────────────────────────────────────────────────────────
    if (site.id === 'athlinks') {
      const total = data?.Result?.TotalCount ?? 0;
      return {
        siteId:      site.id,
        found:       total > 0,
        resultsUrl:  total > 0 ? site.resultsUrl : null,
        resultCount: total,
        notes:       total > 0 ? `${total} results found` : null,
      };
    }

    // Fallback for unknown direct-API sites
    return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0, notes: null };

  } catch (err) {
    return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0,
             notes: `Error: ${err.message}` };
  }
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const body = parseBody(event);

  const { runnerName, dateOfBirth = null, interests = [] } = body;

  if (!runnerName || typeof runnerName !== 'string' || runnerName.trim().length < 2) {
    return errors.badRequest('runnerName is required (minimum 2 characters)');
  }

  const validTags      = ['road','trail','ultra','marathon','parkrun','triathlon','ocr','track','crosscountry'];
  const cleanInterests = Array.isArray(interests)
    ? interests.filter(t => validTags.includes(t))
    : [];

  const name           = runnerName.trim();
  const approximateAge = calcAge(dateOfBirth);
  const sites          = resolveSiteUrls(name, cleanInterests);

  // Split: sites with a direct API vs. those that need Claude web_search
  const directSites = sites.filter(s => s.directApiUrl);
  const searchSites = sites.filter(s => !s.directApiUrl && s.searchQuery);

  // Run direct API calls and Claude search in parallel
  const [directResults, claudeRaw] = await Promise.all([
    Promise.all(directSites.map(s => callDirectApi(s))),
    searchSites.length > 0
      ? discoverRunner({ runnerName: name, approximateAge, sites: searchSites })
      : Promise.resolve([]),
  ]);

  // Normalise Claude results
  const claudeResults = (Array.isArray(claudeRaw) ? claudeRaw : []).map(r => ({
    siteId:      r.siteId,
    found:       !!r.found,
    resultsUrl:  r.resultsUrl  ?? null,
    resultCount: r.resultCount ?? 0,
    notes:       r.notes       ?? null,
  }));

  // Merge all results, preserving the original site order
  const resultMap = Object.fromEntries(
    [...directResults, ...claudeResults].map(r => [r.siteId, r])
  );

  const results = sites.map(s => ({
    siteId:      s.id,
    siteName:    s.name,
    description: s.description,
    ...(resultMap[s.id] ?? { found: false, resultsUrl: null, resultCount: 0, notes: null }),
  }));

  console.log(JSON.stringify({
    type:        'DISCOVER_COMPLETE',
    runner:      name,
    directSites: directSites.map(s => s.id),
    searchSites: searchSites.map(s => s.id),
    found:       results.filter(r => r.found).map(r => r.siteId),
  }));

  return ok({ sites: results });
});
