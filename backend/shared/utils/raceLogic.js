'use strict';

/**
 * shared/utils/raceLogic.js
 * Core business logic — pure functions, fully unit-testable.
 */

// ─── Canonical distance table ─────────────────────────────────────────────────

const CANONICAL_DISTANCES = [
  { key: '1mile',        meters: 1609,   labels: ['1 mile','one mile','mile','1m'] },
  { key: '5k',          meters: 5000,   labels: ['5k','5km','5 km','parkrun','5000m','5000'] },
  { key: '8k',          meters: 8047,   labels: ['8k','5 mile','5m','5mi','8km'] },
  { key: '10k',         meters: 10000,  labels: ['10k','10km','10 km','10000m'] },
  { key: '15k',         meters: 15000,  labels: ['15k','15km'] },
  { key: '10mile',      meters: 16093,  labels: ['10 mile','10m','10mi','10 miles'] },
  { key: '20k',         meters: 20000,  labels: ['20k','20km'] },
  { key: 'halfmarathon',meters: 21097,  labels: ['half marathon','half','13.1','13.1 miles','21k','21km','hm','half-marathon'] },
  { key: '25k',         meters: 25000,  labels: ['25k','25km'] },
  { key: '30k',         meters: 30000,  labels: ['30k','30km'] },
  { key: 'marathon',    meters: 42195,  labels: ['marathon','26.2','26.2 miles','42k','42km','full marathon','full'] },
  { key: '50k',         meters: 50000,  labels: ['50k','50km'] },
  { key: '50mile',      meters: 80467,  labels: ['50 mile','50m','50mi','50 miles'] },
  { key: '100k',        meters: 100000, labels: ['100k','100km'] },
  { key: '100mile',     meters: 160934, labels: ['100 mile','100m','100mi','100 miles'] },
];

/**
 * Normalise an extracted distance to a canonical key.
 * @param {string} label   - Raw label from extraction e.g. "Half Marathon"
 * @param {number|null} meters - Extracted meters if available
 * @returns {{ key: string, meters: number }|null}
 */
function normaliseDistance(label, meters) {
  const cleanLabel = (label || '').toLowerCase().trim().replace(/\s+/g, ' ');

  // 1. Exact label match
  for (const d of CANONICAL_DISTANCES) {
    if (d.labels.includes(cleanLabel)) return { key: d.key, meters: d.meters };
  }

  // 2. Meters match with tolerance
  if (meters && meters > 0) {
    for (const d of CANONICAL_DISTANCES) {
      const tolerance = d.key.includes('trail') ? 0.08 : 0.03;
      if (Math.abs(meters - d.meters) / d.meters <= tolerance) {
        return { key: d.key, meters: d.meters };
      }
    }
    // Return as "other" with the raw meters
    return { key: 'other', meters };
  }

  // 3. Partial label match (last resort)
  for (const d of CANONICAL_DISTANCES) {
    for (const lbl of d.labels) {
      if (cleanLabel.includes(lbl) || lbl.includes(cleanLabel)) {
        return { key: d.key, meters: d.meters };
      }
    }
  }

  return { key: 'other', meters: meters || 0 };
}

// ─── Age calculation ──────────────────────────────────────────────────────────

/**
 * Calculate exact age at race date using DOB.
 * @param {string} dob      - ISO date "YYYY-MM-DD"
 * @param {string} raceDate - ISO date "YYYY-MM-DD"
 * @returns {number}
 */
function calcAgeAtRace(dob, raceDate) {
  const birth = new Date(dob);
  const race  = new Date(raceDate);
  let age = race.getFullYear() - birth.getFullYear();
  const birthdayThisYear = new Date(race.getFullYear(), birth.getMonth(), birth.getDate());
  if (race < birthdayThisYear) age--;
  return age;
}

/**
 * Calculate standard 5-year WMA age group bracket.
 * Under-20 uses narrower groups; 90+ is a single bracket.
 * @param {number} age
 * @returns {string} e.g. "40-44", "18-19", "90+"
 */
function calcAgeGroup(age) {
  if (age < 18) return 'U18';
  if (age < 20) return '18-19';
  if (age >= 90) return '90+';
  const lower = Math.floor(age / 5) * 5;
  return `${lower}-${lower + 4}`;
}

// ─── Race name canonicalisation ───────────────────────────────────────────────

/**
 * Produce a stable canonical slug from a raw race name.
 * Used for deduplication and the race-over-years view.
 * @param {string} rawName
 * @returns {string} e.g. "boston-marathon"
 */
function canonicaliseRaceName(rawName) {
  return (rawName || '')
    .toLowerCase()
    // Strip year (4-digit)
    .replace(/\b(19|20)\d{2}\b/g, '')
    // Strip common sponsor phrases
    .replace(/presented by .+/gi, '')
    .replace(/sponsored by .+/gi, '')
    .replace(/in partnership with .+/gi, '')
    // Strip ordinals (1st, 2nd, 42nd, etc.)
    .replace(/\b\d+(st|nd|rd|th)\b/gi, '')
    // Strip edition words
    .replace(/\b(annual|edition|inaugural|virtual|race|run|classic|open|championship|championships)\b/gi, '')
    // Normalise punctuation and whitespace
    .replace(/[^a-z0-9 ]/g, '')
    .replace(/\s+/g, '-')
    .replace(/^-+|-+$/g, '')
    .trim();
}

// ─── Boston Qualifier standards ───────────────────────────────────────────────

// BQ standards in seconds — update annually from official BAA source
// https://www.baa.org/races/boston-marathon/qualify
const BQ_STANDARDS = {
  // [ageGroup][gender] = cutoff seconds
  '18-34': { M: 10800, F: 12600 }, // 3:00:00 / 3:30:00
  '35-39': { M: 11100, F: 12900 }, // 3:05:00 / 3:35:00
  '40-44': { M: 11400, F: 13200 }, // 3:10:00 / 3:40:00
  '45-49': { M: 12000, F: 13800 }, // 3:20:00 / 3:50:00
  '50-54': { M: 12300, F: 14100 }, // 3:25:00 / 3:55:00
  '55-59': { M: 12900, F: 14700 }, // 3:35:00 / 4:05:00
  '60-64': { M: 13800, F: 15600 }, // 3:50:00 / 4:20:00
  '65-69': { M: 15000, F: 16800 }, // 4:10:00 / 4:40:00
  '70-74': { M: 16200, F: 18000 }, // 4:30:00 / 5:00:00
  '75-79': { M: 17400, F: 19200 }, // 4:50:00 / 5:20:00
  '80+':   { M: 18600, F: 20400 }, // 5:10:00 / 5:40:00
};

/**
 * Check if a marathon result is a Boston Qualifier.
 * @param {number} finishSeconds
 * @param {number} age
 * @param {string} gender  - "M" or "F"
 * @returns {{ isBQ: boolean, bqGapSeconds: number, bqStandardSeconds: number }}
 */
function checkBQ(finishSeconds, age, gender) {
  // Find the applicable BQ age group
  const ageBracket = age >= 80 ? '80+' :
    age >= 75 ? '75-79' : age >= 70 ? '70-74' : age >= 65 ? '65-69' :
    age >= 60 ? '60-64' : age >= 55 ? '55-59' : age >= 50 ? '50-54' :
    age >= 45 ? '45-49' : age >= 40 ? '40-44' : age >= 35 ? '35-39' : '18-34';

  const g = (gender || '').toUpperCase() === 'F' ? 'F' : 'M';
  const standard = BQ_STANDARDS[ageBracket]?.[g];
  if (!standard) return { isBQ: false, bqGapSeconds: null, bqStandardSeconds: null };

  const gap = standard - finishSeconds; // positive = faster than standard
  return {
    isBQ: finishSeconds <= standard,
    bqGapSeconds: gap,
    bqStandardSeconds: standard,
  };
}

// ─── Time formatting ──────────────────────────────────────────────────────────

/**
 * Convert seconds integer to display string.
 * @param {number} seconds
 * @returns {string} e.g. "1:23:45" or "23:45"
 */
function secondsToTime(seconds) {
  if (!seconds || seconds < 0) return null;
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
  return `${m}:${String(s).padStart(2,'0')}`;
}

/**
 * Parse a time string to seconds.
 * Handles: "1:23:45", "23:45", "1:23:45.3", "83:45" (no hours)
 * @param {string} timeStr
 * @returns {number|null}
 */
function timeToSeconds(timeStr) {
  if (!timeStr) return null;
  const clean = String(timeStr).trim().split('.')[0]; // strip sub-seconds
  const parts = clean.split(':').map(Number);
  if (parts.some(isNaN)) return null;
  if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
  if (parts.length === 2) return parts[0] * 60 + parts[1];
  return null;
}

/**
 * Calculate pace per km in seconds.
 * @param {number} finishSeconds
 * @param {number} distanceMeters
 * @returns {number|null}
 */
function calcPacePerKm(finishSeconds, distanceMeters) {
  if (!finishSeconds || !distanceMeters) return null;
  return Math.round(finishSeconds / (distanceMeters / 1000));
}

// ─── Result sorting and filtering ─────────────────────────────────────────────

const SORT_FIELDS = {
  date:         (a, b) => new Date(b.raceDate)  - new Date(a.raceDate),
  distance:     (a, b) => (b.distanceMeters  || 0) - (a.distanceMeters  || 0),
  finishTime:   (a, b) => (b.chipSeconds || b.finishSeconds || 0) - (a.chipSeconds || a.finishSeconds || 0),
  overallPlace: (a, b) => (b.overallPlace || 0) - (a.overallPlace || 0),
  ageGrade:     (a, b) => (b.ageGradePercent || 0) - (a.ageGradePercent || 0),
};

/**
 * Sort and filter a result list.
 * @param {Array}  results
 * @param {Object} opts - { sort, order, distance, surface, yearFrom, yearTo, raceNameSlug, view }
 * @returns {Array}
 */
function applyFiltersAndSort(results, opts = {}) {
  const { sort = 'date', order = 'desc', distance, surface, yearFrom, yearTo, raceNameSlug, view } = opts;

  let filtered = results.filter(r => {
    if (distance && distance !== 'all' && r.distanceCanonical !== distance) return false;
    if (surface  && surface  !== 'all' && r.surface !== surface)            return false;
    if (raceNameSlug && r.raceNameCanonical !== raceNameSlug)               return false;
    const year = r.raceDate ? parseInt(r.raceDate.slice(0, 4)) : 0;
    if (yearFrom && year < yearFrom) return false;
    if (yearTo   && year > yearTo)   return false;
    return true;
  });

  // Special views
  if (view === 'prs') filtered = filtered.filter(r => r.isPR);

  const sortFn = SORT_FIELDS[sort] || SORT_FIELDS.date;
  filtered.sort(sortFn);
  if (order === 'asc') filtered.reverse();

  return filtered;
}

// ─── Exports ──────────────────────────────────────────────────────────────────

module.exports = {
  normaliseDistance,
  calcAgeAtRace,
  calcAgeGroup,
  canonicaliseRaceName,
  checkBQ,
  secondsToTime,
  timeToSeconds,
  calcPacePerKm,
  applyFiltersAndSort,
  CANONICAL_DISTANCES,
  BQ_STANDARDS,
};
