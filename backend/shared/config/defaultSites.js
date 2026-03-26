'use strict';

/**
 * shared/config/defaultSites.js
 *
 * Default running result sites searched during runner discovery.
 * Sites are searched in order — put the most comprehensive aggregators first.
 *
 * To add a new site: add an entry here and redeploy. No app update required.
 *
 * searchUrlTemplate placeholders:
 *   {name}       — full name URL-encoded ("Jane+Smith")
 *   {firstName}  — first word of name, URL-encoded
 *   {lastName}   — last word of name, URL-encoded
 */
const DEFAULT_SITES = [
  {
    id:          'athlinks',
    name:        'Athlinks',
    description: 'Largest race results aggregator — road, trail, triathlon, OCR, cycling',
    searchUrlTemplate: 'https://www.athlinks.com/athletes/search?q={name}',
    enabled:     true,
  },
  {
    id:          'ultrasignup',
    name:        'Ultrasignup',
    description: 'Ultra marathon and trail race results',
    searchUrlTemplate: 'https://ultrasignup.com/results.aspx?fname={firstName}&lname={lastName}',
    enabled:     true,
  },
  {
    id:          'runsignup',
    name:        'RunSignup',
    description: 'Road race results from RunSignup-hosted events across the US',
    searchUrlTemplate: 'https://runsignup.com/Race/Results/Search?search_term={name}',
    enabled:     true,
  },
  {
    id:          'nyrr',
    name:        'New York Road Runners',
    description: 'NYRR races including NYC Marathon, Queens 10K, and more',
    searchUrlTemplate: 'https://results.nyrr.org/search/{name}',
    enabled:     true,
  },
  {
    id:          'baa',
    name:        'Boston Athletic Association',
    description: 'Boston Marathon and BAA road race results',
    searchUrlTemplate: 'https://results.baa.org/2024/?pid=search&search%5Bname%5D={lastName}&search%5Bfirstname%5D={firstName}',
    enabled:     true,
  },
];

/**
 * Returns enabled sites with search URLs resolved for the given runner name.
 * @param {string} fullName  — "Jane Smith"
 * @returns {Array<{id, name, description, searchUrl}>}
 */
function resolveSiteUrls(fullName) {
  const parts     = fullName.trim().split(/\s+/);
  const firstName = encodeURIComponent(parts[0] || '');
  const lastName  = encodeURIComponent(parts[parts.length - 1] || '');
  const fullEnc   = encodeURIComponent(fullName.trim()).replace(/%20/g, '+');

  return DEFAULT_SITES
    .filter(s => s.enabled)
    .map(s => ({
      id:          s.id,
      name:        s.name,
      description: s.description,
      searchUrl:   s.searchUrlTemplate
        .replace('{name}',      fullEnc)
        .replace('{firstName}', firstName)
        .replace('{lastName}',  lastName),
    }));
}

module.exports = { DEFAULT_SITES, resolveSiteUrls };
