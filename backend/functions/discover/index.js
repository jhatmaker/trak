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
 * The Athlinks search endpoint returns athlete(s) matching the name, each with
 * a nested array of race results. We take the first athlete match and map
 * their results to the DiscoverResultRecord shape.
 */
function parseAthlinksDirect(site, data) {
  const total = data?.Result?.TotalCount ?? 0;
  if (total === 0) {
    return { siteId: site.id, found: false, resultsUrl: null, resultCount: 0, results: [], notes: null };
  }

  // Try to extract individual results from the first athlete match
  const athletes = data?.Result?.Results;
  const athlete  = Array.isArray(athletes) && athletes.length > 0 ? athletes[0] : null;

  if (!athlete) {
    // Found count > 0 but can't parse structure — return placeholder
    return {
      siteId:      site.id,
      found:       true,
      resultsUrl:  site.resultsUrl ?? null,
      resultCount: total,
      results:     [],
      notes:       `${total} results found`,
    };
  }

  const athleteId  = athlete.ID ?? athlete.AthleteId ?? null;
  const athleteUrl = athleteId
    ? `https://www.athlinks.com/athletes/${athleteId}/results`
    : (site.resultsUrl ?? null);

  // Map each race entry to a DiscoverResultRecord
  const raceEntries = athlete.Races ?? athlete.Results ?? athlete.Events ?? [];
  const results = [];

  for (const race of raceEntries) {
    // Athlinks date is sometimes a .NET JSON Date string: "/Date(1713225600000)/"
    let raceDate = null;
    const rawDate = race.EventDate ?? race.RaceDate ?? race.Date ?? null;
    if (rawDate) {
      const dotNetMatch = String(rawDate).match(/\/Date\((\d+)\)\//);
      if (dotNetMatch) {
        raceDate = new Date(parseInt(dotNetMatch[1])).toISOString().slice(0, 10);
      } else if (/^\d{4}-\d{2}-\d{2}/.test(rawDate)) {
        raceDate = rawDate.slice(0, 10);
      }
    }

    const eventId   = race.EventID   ?? race.EventId   ?? null;
    const courseId  = race.CourseID  ?? race.CourseId  ?? null;
    const resultId  = athleteId && eventId ? `athlinks-${athleteId}-${eventId}${courseId ? '-' + courseId : ''}` : null;

    const finishSeconds = race.RaceTime ?? race.FinishSeconds ?? race.NetTime ?? 0;
    const overallPlace  = race.OverallRank ?? race.OverallPlace ?? race.Place ?? 0;
    const overallTotal  = race.FieldSize   ?? race.TotalEntrants ?? race.OverallTotal ?? 0;

    const city  = race.EventCity  ?? race.City  ?? athlete.City  ?? null;
    const state = race.EventState ?? race.State ?? athlete.State ?? null;
    const location = city && state ? `${city}, ${state}` : (city ?? state ?? null);

    const distLabel   = race.CourseName ?? race.DistanceLabel ?? race.Distance ?? null;
    const distMeters  = race.DistanceInMeters ?? race.DistanceMeters ?? 0;

    const resultUrl = eventId && athleteId
      ? `https://www.athlinks.com/event/${eventId}/results/runner/${athleteId}`
      : athleteUrl;

    results.push({
      resultId,
      raceName:       race.EventName ?? race.RaceName ?? null,
      raceDate,
      distanceLabel:  distLabel,
      distanceMeters: typeof distMeters === 'number' ? distMeters : 0,
      location,
      bibNumber:      race.BibNumber ? String(race.BibNumber) : null,
      finishTime:     finishSeconds > 0 ? secondsToTimeStr(finishSeconds) : null,
      finishSeconds:  typeof finishSeconds === 'number' ? finishSeconds : 0,
      overallPlace:   typeof overallPlace === 'number' ? overallPlace : 0,
      overallTotal:   typeof overallTotal === 'number' ? overallTotal : 0,
      resultsUrl:     resultUrl,
    });
  }

  return {
    siteId:      site.id,
    found:       true,
    resultsUrl:  athleteUrl,
    resultCount: results.length || total,
    results,
    notes:       null,
  };
}

function secondsToTimeStr(s) {
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return `${h}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`;
  return `${m}:${String(sec).padStart(2,'0')}`;
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = wrap(async (event) => {
  const body = parseBody(event);

  const {
    runnerName,
    dateOfBirth    = null,
    interests      = [],
    excludeSiteIds = [],
    extractResults = true,   // false → cheap check only (background worker)
    sinceDate      = null,   // YYYY-MM-DD — incremental: only return results after this date
  } = body;

  if (!runnerName || typeof runnerName !== 'string' || runnerName.trim().length < 2) {
    return errors.badRequest('runnerName is required (minimum 2 characters)');
  }

  const validTags      = ['road','trail','ultra','marathon','parkrun','triathlon','ocr','track','crosscountry'];
  const cleanInterests = Array.isArray(interests)
    ? interests.filter(t => validTags.includes(t))
    : [];

  const cleanExclude = Array.isArray(excludeSiteIds) ? excludeSiteIds.filter(s => typeof s === 'string') : [];

  const name           = runnerName.trim();
  const approximateAge = calcAge(dateOfBirth);
  const sites          = resolveSiteUrls(name, cleanInterests, cleanExclude);

  // Split: sites with a direct API vs. those that need Claude web_search
  const directSites = sites.filter(s => s.directApiUrl);
  const searchSites = sites.filter(s => !s.directApiUrl && s.searchQuery);

  // Run direct API calls and Claude search in parallel
  const [directResults, claudeRaw] = await Promise.all([
    Promise.all(directSites.map(s => callDirectApi(s))),
    searchSites.length > 0
      ? discoverRunner({ runnerName: name, approximateAge, sites: searchSites, extractResults, sinceDate })
      : Promise.resolve([]),
  ]);

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

  const totalResults = results.reduce((n, r) => n + (Array.isArray(r.results) ? r.results.length : 0), 0);
  console.log(JSON.stringify({
    type:         'DISCOVER_COMPLETE',
    runner:       name,
    directSites:  directSites.map(s => s.id),
    searchSites:  searchSites.map(s => s.id),
    found:        results.filter(r => r.found).map(r => r.siteId),
    totalResults,
  }));

  return ok({ sites: results });
});
