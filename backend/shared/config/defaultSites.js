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
// Stable GUIDs — must stay in sync with SourcesRepository.java on Android.
// Format: 00000000-0000-0000-0000-00000000000N (sequential, easy to spot in logs)
const DEFAULT_SITES = [
  {
    guid:                '00000000-0000-0000-0000-000000000001',
    id:                  'athlinks',
    name:                'Athlinks',
    description:         'Largest race results aggregator — road, trail, triathlon, OCR, cycling',
    // Direct API — no Claude needed. Returns unclaimed results from Athlinks database.
    directApiUrlTemplate: 'https://alaska.athlinks.com/Result/api/Search?searchTerm={nameEncoded}',
    resultsUrlTemplate:   'https://www.athlinks.com/search/unclaimed?category=unclaimed&term={nameEncoded}',
    searchQueryTemplate:  null,
    tags:                ['road', 'trail', 'ultra', 'marathon', 'triathlon', 'ocr', 'track'],
    priority:            'always',
    enabled:             true,
  },
  {
    guid:        '00000000-0000-0000-0000-000000000002',
    id:          'ultrasignup',
    name:        'Ultrasignup',
    description: 'Ultra marathon and trail race results',
    searchQueryTemplate: '"{name}" ultrasignup race results',
    tags:        ['trail', 'ultra'],
    priority:    'normal',
    enabled:     true,
  },
  {
    guid:        '00000000-0000-0000-0000-000000000003',
    id:          'runsignup',
    name:        'RunSignup',
    description: 'Road race results from RunSignup-hosted events across the US',
    searchQueryTemplate: '"{name}" runsignup race results',
    tags:        ['road', 'trail', 'marathon'],
    priority:    'normal',
    enabled:     true,
  },
  {
    guid:        '00000000-0000-0000-0000-000000000004',
    id:          'nyrr',
    name:        'New York Road Runners',
    description: 'NYRR races including NYC Marathon, Queens 10K, and more',
    searchQueryTemplate: '"{name}" NYRR race results runner',
    tags:        ['road', 'marathon'],
    priority:    'normal',
    enabled:     true,
  },
  {
    guid:        '00000000-0000-0000-0000-000000000005',
    id:          'baa',
    name:        'Boston Athletic Association',
    description: 'Boston Marathon and BAA road race results',
    searchQueryTemplate: '"{name}" Boston Marathon BAA race results',
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
function resolveSiteUrls(fullName, interests = [], excludeIds = []) {
  const parts       = fullName.trim().split(/\s+/);
  const firstName   = parts[0] || '';
  const lastName    = parts[parts.length - 1] || '';
  const nameEncoded = encodeURIComponent(fullName.trim());

  const hasInterests = interests.length > 0;

  return DEFAULT_SITES
    .filter(s => s.enabled)
    .filter(s => !excludeIds.includes(s.id))
    .filter(s => {
      if (!hasInterests)            return true;
      if (s.priority === 'always')  return true;
      return s.tags.some(tag => interests.includes(tag));
    })
    .map(s => ({
      id:           s.id,
      name:         s.name,
      description:  s.description,
      tags:         s.tags,
      // Direct API sites — call without Claude
      directApiUrl: s.directApiUrlTemplate
        ? s.directApiUrlTemplate.replace('{nameEncoded}', nameEncoded)
        : null,
      resultsUrl:   s.resultsUrlTemplate
        ? s.resultsUrlTemplate.replace('{nameEncoded}', nameEncoded)
        : null,
      // Web-search sites — passed to Claude
      searchQuery:  s.searchQueryTemplate
        ? s.searchQueryTemplate
            .replace('{name}',      fullName.trim())
            .replace('{firstName}', firstName)
            .replace('{lastName}',  lastName)
        : null,
    }));
}

/**
 * Resolve site configs for a specific list of source GUIDs.
 * Used when the client sends `sourceIds` instead of interests/excludeIds.
 * Unknown GUIDs (e.g. custom source UUIDs) are silently ignored — custom
 * source URLs aren't stored on the backend yet and are skipped.
 *
 * @param {string}   fullName  — "Jane Smith"
 * @param {string[]} guids     — e.g. ["00000000-0000-0000-0000-000000000001", ...]
 * @returns {Array}
 */
function resolveSiteUrlsByGuid(fullName, guids = []) {
  const parts       = fullName.trim().split(/\s+/);
  const firstName   = parts[0] || '';
  const lastName    = parts[parts.length - 1] || '';
  const nameEncoded = encodeURIComponent(fullName.trim());

  const guidSet = new Set(guids);

  return DEFAULT_SITES
    .filter(s => s.enabled && guidSet.has(s.guid))
    .map(s => ({
      id:           s.id,
      name:         s.name,
      description:  s.description,
      tags:         s.tags,
      directApiUrl: s.directApiUrlTemplate
        ? s.directApiUrlTemplate.replace('{nameEncoded}', nameEncoded)
        : null,
      resultsUrl:   s.resultsUrlTemplate
        ? s.resultsUrlTemplate.replace('{nameEncoded}', nameEncoded)
        : null,
      searchQuery:  s.searchQueryTemplate
        ? s.searchQueryTemplate
            .replace('{name}',      fullName.trim())
            .replace('{firstName}', firstName)
            .replace('{lastName}',  lastName)
        : null,
    }));
}

module.exports = { DEFAULT_SITES, resolveSiteUrls, resolveSiteUrlsByGuid };
