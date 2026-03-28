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
const { resolveSiteUrls,
        resolveSiteUrlsByGuid }       = require('/opt/nodejs/shared/config/defaultSites');
const db                              = require('/opt/nodejs/shared/db/client');

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
 * Returns individual race results where the API supports it.
 */
async function callDirectApi(site) {
  try {
    const res = await fetch(site.directApiUrl, {
      headers: { 'Accept': 'application/json', 'User-Agent': 'Trak/1.0' },
      signal:  AbortSignal.timeout(10_000),
    });

    if (!res.ok) {
      return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0,
               results: [], notes: `API returned HTTP ${res.status}` };
    }

    const data = await res.json();

    // ── Athlinks ──────────────────────────────────────────────────────────────
    if (site.id === 'athlinks') {
      return parseAthlinksDirect(site, data);
    }

    // Fallback for unknown direct-API sites
    return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0, results: [], notes: null };

  } catch (err) {
    return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0,
             results: [], notes: `Error: ${err.message}` };
  }
}

/**
 * Parse an Athlinks search API response into individual race result records.
 *
 * The Athlinks /Result/api/Search endpoint returns:
 *   Result.TotalCount  — total athletes matching the name
 *   Result.OverLimit   — true when name is too common to identify one person
 *   Result.RaceList    — list of groups, each group is a list of race entries
 *
 * Each race entry has: Name, StartDateTime, StateProv, Country, Time,
 * EntryId, EventId, CourseId, EventCourseId, BibNum, DisplayName, Age, Gender.
 */
function parseAthlinksDirect(site, data) {
  const total     = data?.Result?.TotalCount ?? 0;
  const overLimit = data?.Result?.OverLimit === true || data?.Result?.OverLimit === 'True';

  if (total === 0) {
    return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0, results: [], notes: null };
  }

  if (overLimit) {
    // Name matches too many athletes — can't identify the specific runner.
    // Return not-found so the user knows Athlinks has records but needs a more specific name.
    return {
      siteId:      site.id,
      found:       false,
      resultsUrl:  site.resultsUrl ?? null,
      resultCount: 0,
      results:     [],
      notes:       `Name too common — ${total} athletes found on Athlinks. Try adding a middle name or initial.`,
    };
  }

  // Flatten RaceList (list of groups, each group is a list of race entries)
  const raceListGroups = Array.isArray(data?.Result?.RaceList) ? data.Result.RaceList : [];
  const allRaces = [];
  for (const group of raceListGroups) {
    if (Array.isArray(group)) allRaces.push(...group);
    else if (group)           allRaces.push(group);
  }

  if (allRaces.length === 0) {
    return {
      siteId:      site.id,
      found:       true,
      resultsUrl:  site.resultsUrl ?? null,
      resultCount: total,
      results:     [],
      notes:       `${total} races found on Athlinks`,
    };
  }

  // Build a results-page URL from the matched alias name (most accurate for this person)
  const aliasName     = data?.Result?.Aliases?.[0]?.Name ?? null;
  const aliasEncoded  = aliasName ? encodeURIComponent(aliasName) : null;
  const athleteUrl    = aliasEncoded
    ? `https://www.athlinks.com/search/unclaimed?category=unclaimed&term=${aliasEncoded}`
    : (site.resultsUrl ?? null);

  const results = [];

  for (const race of allRaces) {
    // Date: "2025-06-28T08:00:00" → "2025-06-28"
    const raceDate = race.StartDateTime ? race.StartDateTime.slice(0, 10) : null;

    // Stable dedup ID: prefer EntryId, fallback to EventId+CourseId
    const entryId  = race.EntryId   ?? null;
    const eventId  = race.EventId   ?? null;
    const courseId = race.CourseId  ?? null;
    const resultId = entryId
      ? `athlinks-entry-${entryId}`
      : (eventId && courseId ? `athlinks-${eventId}-${courseId}` : null);

    const finishSeconds = race.Time ? timeStrToSeconds(race.Time) : 0;

    const raceState   = race.StateProv ?? null;
    const raceCountry = race.Country   ?? null;
    // Combined string kept for display; separate fields used when claiming
    const location = raceState && raceCountry ? `${raceState}, ${raceCountry}` : (raceState ?? raceCountry ?? null);

    const resultUrl = eventId && entryId
      ? `https://www.athlinks.com/event/${eventId}/results/entry/${entryId}`
      : athleteUrl;

    results.push({
      resultId,
      raceName:       race.Name         ?? null,
      raceDate,
      distanceLabel:  race.Name         ?? null, // race name usually contains distance
      distanceMeters: 0,                          // not available from this endpoint
      location,
      raceCity:       null,                       // Athlinks search API does not return city
      raceState,
      raceCountry,
      bibNumber:      race.BibNum ? String(race.BibNum) : null,
      finishTime:     race.Time         ?? null,
      finishSeconds,
      overallPlace:   0,                          // not available from this endpoint
      overallTotal:   0,
      resultsUrl:     resultUrl,
    });
  }

  return {
    siteId:      site.id,
    found:       true,
    resultsUrl:  athleteUrl,
    resultCount: results.length,
    results,
    notes:       null,
  };
}

/** Convert "H:MM:SS" or "M:SS" finish time string to total seconds. */
function timeStrToSeconds(t) {
  if (!t) return 0;
  const parts = String(t).split(':').map(Number);
  if (parts.length === 3) return (parts[0] * 3600) + (parts[1] * 60) + parts[2];
  if (parts.length === 2) return (parts[0] * 60) + parts[1];
  return 0;
}


// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const body = parseBody(event);

  const {
    userId          = null,   // device-local UUID — used to load/store counts in DynamoDB
    sourceIds       = null,   // array of source GUIDs — when present, overrides interests/excludeSiteIds
    customSources   = [],     // [{id, name, url}] — user-added sites not in DEFAULT_SITES
    runnerName,
    dateOfBirth     = null,
    interests       = [],
    excludeSiteIds  = [],
    extractResults  = true,   // false → cheap check only (background worker)
    sinceDate       = null,   // YYYY-MM-DD — incremental: only return results after this date
    lastKnownCounts = {},     // siteId → last known result count for free pre-check (Android fallback)
  } = body;

  if (!runnerName || typeof runnerName !== 'string' || runnerName.trim().length < 2) {
    return errors.badRequest('runnerName is required (minimum 2 characters)');
  }

  const name           = runnerName.trim();
  const approximateAge = calcAge(dateOfBirth);

  // Resolve which sites to search:
  //   sourceIds present → GUID-based selection (new architecture)
  //   otherwise         → interest/exclude filter (legacy fallback)
  let sites;
  if (Array.isArray(sourceIds) && sourceIds.length > 0) {
    sites = resolveSiteUrlsByGuid(name, sourceIds);
  } else {
    const validTags      = ['road','trail','ultra','marathon','parkrun','triathlon','ocr','track','crosscountry'];
    const cleanInterests = Array.isArray(interests) ? interests.filter(t => validTags.includes(t)) : [];
    const cleanExclude   = Array.isArray(excludeSiteIds) ? excludeSiteIds.filter(s => typeof s === 'string') : [];
    sites = resolveSiteUrls(name, cleanInterests, cleanExclude);
  }

  // Append user-added custom sources as Claude search targets.
  // Each becomes a search site with a site:-restricted web query.
  // They never have a direct API so they always go to Claude.
  if (Array.isArray(customSources) && customSources.length > 0) {
    for (const cs of customSources) {
      if (!cs.id || !cs.url) continue;
      let hostname = cs.url;
      try { hostname = new URL(cs.url).hostname; } catch { /* use raw url */ }
      sites.push({
        id:          cs.id,
        name:        cs.name || hostname,
        description: cs.name || hostname,
        searchQuery: `"${name}" site:${hostname}`,
        resultsUrl:  cs.url,
      });
    }
  }

  // Load stored per-user counts from DynamoDB if userId is present.
  // These take precedence over lastKnownCounts sent from the device.
  let storedCounts = lastKnownCounts;
  if (userId) {
    try {
      const record = await db.siteCounts.get(userId);
      if (record && record.counts) storedCounts = record.counts;
    } catch (err) {
      console.log(JSON.stringify({ type: 'SITE_COUNTS_READ_ERROR', userId, err: err.message }));
    }
  }

  // Split: sites with a direct API vs. those that need Claude web_search
  const directSites = sites.filter(s => s.directApiUrl);
  const searchSites = sites.filter(s => !s.directApiUrl && s.searchQuery);

  // Run direct API calls first (free — no Claude tokens)
  const directResults = await Promise.all(directSites.map(s => callDirectApi(s)));

  // Build current site counts from direct results for the response
  const siteResultCounts = {};
  for (const r of directResults) siteResultCounts[r.siteId] = r.resultCount ?? 0;

  // ── Free pre-check (cheap mode only) ──────────────────────────────────────
  // Compare each direct-API site's current count to the stored value.
  // storedCounts comes from DynamoDB (when userId present) or from the request.
  // If ALL direct sites are unchanged, skip Claude entirely — nothing new to find.
  if (!extractResults && Object.keys(storedCounts).length > 0) {
    const allUnchanged = directResults.every(r => {
      const known = storedCounts[r.siteId];
      return known !== undefined && r.resultCount === known;
    });
    if (allUnchanged && directResults.length > 0) {
      console.log(JSON.stringify({
        type:   'DISCOVER_NO_CHANGE',
        runner: name,
        counts: siteResultCounts,
      }));
      return ok({ noChange: true, sites: [], siteResultCounts });
    }
  }

  // Call Claude for search sites (or when pre-check determined something changed)
  const claudeRaw = searchSites.length > 0
    ? await discoverRunner({ runnerName: name, approximateAge, sites: searchSites, extractResults, sinceDate })
    : [];

  // Normalise Claude results.
  // Cheap-check responses have no results array; full-extract responses do.
  const claudeResults = (Array.isArray(claudeRaw) ? claudeRaw : []).map(r => ({
    siteId:      r.siteId,
    found:       !!r.found,
    resultsUrl:  r.athleteUrl  ?? r.resultsUrl ?? null,
    resultCount: Array.isArray(r.results) ? r.results.length : (r.resultCount ?? 0),
    results:     extractResults && Array.isArray(r.results)
      ? r.results.map(rec => ({
          resultId:      rec.resultId      ?? null,
          raceName:      rec.raceName      ?? null,
          raceDate:      rec.raceDate      ?? null,
          distanceLabel: rec.distanceLabel ?? null,
          distanceMeters: typeof rec.distanceMeters === 'number' ? rec.distanceMeters : 0,
          location:      rec.location      ?? null,
          bibNumber:     rec.bibNumber     ?? null,
          finishTime:    rec.finishTime    ?? null,
          finishSeconds: typeof rec.finishSeconds === 'number' ? rec.finishSeconds : 0,
          overallPlace:  typeof rec.overallPlace  === 'number' ? rec.overallPlace  : 0,
          overallTotal:  typeof rec.overallTotal  === 'number' ? rec.overallTotal  : 0,
          resultsUrl:    rec.resultsUrl    ?? null,
        }))
      : [],
    notes:       r.notes ?? null,
  }));

  // Merge all results, preserving the original site order
  const resultMap = Object.fromEntries(
    [...directResults, ...claudeResults].map(r => [r.siteId, r])
  );

  const results = sites.map(s => ({
    siteId:      s.id,
    siteName:    s.name,
    description: s.description,
    ...(resultMap[s.id] ?? { found: false, resultsUrl: null, resultCount: 0, results: [], notes: null }),
  }));

  // Accumulate Claude site counts into siteResultCounts for Android to store
  for (const r of claudeResults) siteResultCounts[r.siteId] = r.resultCount ?? 0;

  // Persist updated counts to DynamoDB when userId is present
  if (userId && Object.keys(siteResultCounts).length > 0) {
    try {
      await db.siteCounts.put(userId, siteResultCounts);
    } catch (err) {
      console.log(JSON.stringify({ type: 'SITE_COUNTS_WRITE_ERROR', userId, err: err.message }));
    }
  }

  const totalResults = results.reduce((n, r) => n + (Array.isArray(r.results) ? r.results.length : 0), 0);
  console.log(JSON.stringify({
    type:         'DISCOVER_COMPLETE',
    runner:       name,
    directSites:  directSites.map(s => s.id),
    searchSites:  searchSites.map(s => s.id),
    found:        results.filter(r => r.found).map(r => r.siteId),
    totalResults,
  }));

  return ok({ noChange: false, sites: results, siteResultCounts });
});
