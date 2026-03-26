'use strict';

/**
 * shared/config/defaultSites.js
 *
 * Curated default running result sites searched during runner discovery.
 * Sites are searched in order — put the most comprehensive aggregators first.
 *
 * To add a new site: add an entry here and redeploy. No app update required.
 *
 * priority:
 *   'always'  — included in every discovery scan regardless of runner interests
 *               (broad aggregators like Athlinks that cover all race types)
 *   'normal'  — only included when the runner has at least one matching interest tag,
 *               or when no interests are declared (search everything)
 *
 * tags: race types this site is relevant for.
 *   road, trail, ultra, marathon, parkrun, triathlon, ocr, track, crosscountry
 *
 * searchQueryTemplate placeholders (used as a web search query — Google-style):
 *   {name}       — full name quoted ("Jane Smith")
 *   {firstName}  — first word of name
 *   {lastName}   — last word of name
 *
 * These are passed to Claude's web_search tool as search queries, not fetched as URLs.
 * Most running result sites are JavaScript-rendered and can't be fetched directly —
 * but their athlete pages are indexed by Google and reliably found via site: searches.
 */
const DEFAULT_SITES = [
  {
    id:          'athlinks',
    name:        'Athlinks',
    description: 'Largest race results aggregator — road, trail, triathlon, OCR, cycling',
    searchQueryTemplate: 'site:athlinks.com "{name}"',
    tags:        ['road', 'trail', 'ultra', 'marathon', 'triathlon', 'ocr', 'track'],
    priority:    'always',
    enabled:     true,
  },
  {
    id:          'ultrasignup',
    name:        'Ultrasignup',
    description: 'Ultra marathon and trail race results',
    searchQueryTemplate: 'site:ultrasignup.com "{name}"',
    tags:        ['trail', 'ultra'],
    priority:    'normal',
    enabled:     true,
  },
  {
    id:          'runsignup',
    name:        'RunSignup',
    description: 'Road race results from RunSignup-hosted events across the US',
    searchQueryTemplate: 'site:runsignup.com "{name}" race results',
    tags:        ['road', 'trail', 'marathon'],
    priority:    'normal',
    enabled:     true,
  },
  {
    id:          'nyrr',
    name:        'New York Road Runners',
    description: 'NYRR races including NYC Marathon, Queens 10K, and more',
    searchQueryTemplate: 'site:results.nyrr.org "{name}"',
    tags:        ['road', 'marathon'],
    priority:    'normal',
    enabled:     true,
  },
  {
    id:          'baa',
    name:        'Boston Athletic Association',
    description: 'Boston Marathon and BAA road race results',
    searchQueryTemplate: 'site:results.baa.org "{name}"',
    tags:        ['road', 'marathon'],
    priority:    'normal',
    enabled:     true,
  },
];

/**
 * Returns enabled sites with search URLs resolved for the given runner name.
 * Filters by interests when provided:
 *   - 'always' priority sites are always included
 *   - 'normal' sites included only if they share at least one tag with the runner's interests
 *   - If interests is empty, all enabled sites are returned (no filtering)
 *
 * @param {string}   fullName   — "Jane Smith"
 * @param {string[]} interests  — e.g. ["trail", "ultra"] — from runner profile
 * @returns {Array<{id, name, description, searchUrl, tags}>}
 */
function resolveSiteUrls(fullName, interests = []) {
  const parts     = fullName.trim().split(/\s+/);
  const firstName = parts[0] || '';
  const lastName  = parts[parts.length - 1] || '';

  const hasInterests = interests.length > 0;

  return DEFAULT_SITES
    .filter(s => s.enabled)
    .filter(s => {
      if (!hasInterests)            return true;  // no filter — search all
      if (s.priority === 'always')  return true;  // always include broad aggregators
      return s.tags.some(tag => interests.includes(tag));
    })
    .map(s => ({
      id:          s.id,
      name:        s.name,
      description: s.description,
      tags:        s.tags,
      searchQuery: s.searchQueryTemplate
        .replace('{name}',      fullName.trim())
        .replace('{firstName}', firstName)
        .replace('{lastName}',  lastName),
    }));
}

module.exports = { DEFAULT_SITES, resolveSiteUrls };
