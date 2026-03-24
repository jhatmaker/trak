'use strict';

/**
 * tests/unit/raceLogic.test.js
 * Unit tests for all pure functions in shared/utils/raceLogic.js
 */

const {
  normaliseDistance, calcAgeAtRace, calcAgeGroup,
  canonicaliseRaceName, checkBQ, secondsToTime,
  timeToSeconds, calcPacePerKm, applyFiltersAndSort,
} = require('../../shared/utils/raceLogic');

// ─── normaliseDistance ────────────────────────────────────────────────────────

describe('normaliseDistance', () => {
  test('exact label matches', () => {
    expect(normaliseDistance('5k', null).key).toBe('5k');
    expect(normaliseDistance('Half Marathon', null).key).toBe('halfmarathon');
    expect(normaliseDistance('marathon', null).key).toBe('marathon');
    expect(normaliseDistance('50 Mile', null).key).toBe('50mile');
    expect(normaliseDistance('100k', null).key).toBe('100k');
  });

  test('case-insensitive label match', () => {
    expect(normaliseDistance('HALF MARATHON', null).key).toBe('halfmarathon');
    expect(normaliseDistance('Marathon', null).key).toBe('marathon');
  });

  test('common aliases', () => {
    expect(normaliseDistance('13.1', null).key).toBe('halfmarathon');
    expect(normaliseDistance('26.2', null).key).toBe('marathon');
    expect(normaliseDistance('parkrun', null).key).toBe('5k');
    expect(normaliseDistance('Full', null).key).toBe('marathon');
    expect(normaliseDistance('HM', null).key).toBe('halfmarathon');
  });

  test('meters fallback within 3% tolerance', () => {
    expect(normaliseDistance('Some Race', 5050).key).toBe('5k');     // 1% over
    expect(normaliseDistance('Some Race', 42000).key).toBe('marathon'); // within 3%
    expect(normaliseDistance('Some Race', 21200).key).toBe('halfmarathon');
  });

  test('meters outside tolerance returns other', () => {
    expect(normaliseDistance('Weird Race', 7000).key).toBe('other');
  });

  test('unknown label and no meters returns other', () => {
    expect(normaliseDistance('Crazy Ultra', null).key).toBe('other');
  });

  test('returns correct canonical meters', () => {
    expect(normaliseDistance('marathon', null).meters).toBe(42195);
    expect(normaliseDistance('5k', null).meters).toBe(5000);
    expect(normaliseDistance('halfmarathon', null).meters).toBe(21097);
  });
});

// ─── calcAgeAtRace ────────────────────────────────────────────────────────────

describe('calcAgeAtRace', () => {
  test('birthday before race date — correct age', () => {
    expect(calcAgeAtRace('1983-03-15', '2024-06-01')).toBe(41);
  });

  test('birthday after race date — subtract 1', () => {
    expect(calcAgeAtRace('1983-09-15', '2024-06-01')).toBe(40);
  });

  test('birthday on race date exactly', () => {
    expect(calcAgeAtRace('1983-06-01', '2024-06-01')).toBe(41);
  });

  test('leap year birthday', () => {
    expect(calcAgeAtRace('1984-02-29', '2024-03-01')).toBe(40);
  });

  test('young runner', () => {
    expect(calcAgeAtRace('2005-05-01', '2024-05-15')).toBe(19);
  });
});

// ─── calcAgeGroup ─────────────────────────────────────────────────────────────

describe('calcAgeGroup', () => {
  test('standard 5-year brackets', () => {
    expect(calcAgeGroup(40)).toBe('40-44');
    expect(calcAgeGroup(44)).toBe('40-44');
    expect(calcAgeGroup(45)).toBe('45-49');
    expect(calcAgeGroup(35)).toBe('35-39');
    expect(calcAgeGroup(20)).toBe('20-24');
  });

  test('special cases', () => {
    expect(calcAgeGroup(17)).toBe('U18');
    expect(calcAgeGroup(18)).toBe('18-19');
    expect(calcAgeGroup(19)).toBe('18-19');
    expect(calcAgeGroup(90)).toBe('90+');
    expect(calcAgeGroup(95)).toBe('90+');
  });
});

// ─── canonicaliseRaceName ─────────────────────────────────────────────────────

describe('canonicaliseRaceName', () => {
  test('strips year', () => {
    expect(canonicaliseRaceName('2024 Boston Marathon')).toBe('boston-marathon');
    expect(canonicaliseRaceName('Boston Marathon 2019')).toBe('boston-marathon');
  });

  test('strips sponsor', () => {
    expect(canonicaliseRaceName('Boston Marathon Presented by Bank of America')).toBe('boston-marathon');
  });

  test('strips ordinals', () => {
    expect(canonicaliseRaceName('42nd Annual Turkey Trot')).toBe('turkey-trot');
  });

  test('normalises to slug', () => {
    expect(canonicaliseRaceName('Chicago  Marathon')).toBe('chicago-marathon');
    expect(canonicaliseRaceName("Quincy's 5K Classic!")).toBe('quincys-5k');
  });

  test('same race, different years produce same slug', () => {
    const slug2022 = canonicaliseRaceName('2022 Boston Marathon');
    const slug2024 = canonicaliseRaceName('Boston Marathon 2024 Presented by BAA');
    expect(slug2022).toBe(slug2024);
  });

  test('handles null/empty gracefully', () => {
    expect(canonicaliseRaceName('')).toBe('');
    expect(canonicaliseRaceName(null)).toBe('');
  });
});

// ─── checkBQ ─────────────────────────────────────────────────────────────────

describe('checkBQ', () => {
  test('qualifying time M40-44 (3:10:00 = 11400s)', () => {
    const r = checkBQ(11399, 42, 'M');
    expect(r.isBQ).toBe(true);
    expect(r.bqGapSeconds).toBe(1);
  });

  test('exactly at standard', () => {
    const r = checkBQ(11400, 42, 'M');
    expect(r.isBQ).toBe(true);
    expect(r.bqGapSeconds).toBe(0);
  });

  test('one second over', () => {
    const r = checkBQ(11401, 42, 'M');
    expect(r.isBQ).toBe(false);
    expect(r.bqGapSeconds).toBe(-1);
  });

  test('female standard', () => {
    const r = checkBQ(13199, 42, 'F'); // 3:39:59 — under F40-44 3:40:00
    expect(r.isBQ).toBe(true);
  });

  test('older age group', () => {
    const r = checkBQ(15001, 67, 'M'); // over 65-69 M 4:10:00 = 15000s
    expect(r.isBQ).toBe(false);
  });
});

// ─── secondsToTime / timeToSeconds ───────────────────────────────────────────

describe('secondsToTime', () => {
  test('converts correctly', () => {
    expect(secondsToTime(5025)).toBe('1:23:45');
    expect(secondsToTime(1335)).toBe('22:15');
    expect(secondsToTime(3600)).toBe('1:00:00');
    expect(secondsToTime(60)).toBe('1:00');
  });

  test('null/zero inputs', () => {
    expect(secondsToTime(null)).toBeNull();
    expect(secondsToTime(0)).toBeNull();
  });
});

describe('timeToSeconds', () => {
  test('parses HH:MM:SS', () => {
    expect(timeToSeconds('1:23:45')).toBe(5025);
    expect(timeToSeconds('3:00:00')).toBe(10800);
  });

  test('parses MM:SS', () => {
    expect(timeToSeconds('22:15')).toBe(1335);
  });

  test('strips sub-seconds', () => {
    expect(timeToSeconds('1:23:45.3')).toBe(5025);
  });

  test('null/invalid', () => {
    expect(timeToSeconds(null)).toBeNull();
    expect(timeToSeconds('abc')).toBeNull();
  });
});

// ─── calcPacePerKm ────────────────────────────────────────────────────────────

describe('calcPacePerKm', () => {
  test('5K in 22:15', () => {
    const pace = calcPacePerKm(1335, 5000);
    expect(pace).toBe(267); // 4:27/km
  });

  test('marathon in 3:30:00', () => {
    const pace = calcPacePerKm(12600, 42195);
    expect(pace).toBe(299); // ~4:59/km
  });

  test('null inputs', () => {
    expect(calcPacePerKm(null, 5000)).toBeNull();
    expect(calcPacePerKm(1335, null)).toBeNull();
    expect(calcPacePerKm(0, 5000)).toBeNull();
  });
});

// ─── applyFiltersAndSort ──────────────────────────────────────────────────────

const SAMPLE_RESULTS = [
  { id: '1', raceDate: '2024-06-01', distanceCanonical: '5k',      finishSeconds: 1335, surface: 'road',  isPR: true  },
  { id: '2', raceDate: '2023-10-15', distanceCanonical: 'marathon', finishSeconds: 12600,surface: 'road',  isPR: true  },
  { id: '3', raceDate: '2022-04-03', distanceCanonical: '10k',      finishSeconds: 2800, surface: 'trail', isPR: false },
  { id: '4', raceDate: '2024-09-22', distanceCanonical: '5k',       finishSeconds: 1400, surface: 'road',  isPR: false },
];

describe('applyFiltersAndSort', () => {
  test('default: sort by date desc', () => {
    const results = applyFiltersAndSort(SAMPLE_RESULTS);
    expect(results[0].id).toBe('4'); // 2024-09-22
    expect(results[1].id).toBe('1'); // 2024-06-01
  });

  test('filter by distance', () => {
    const results = applyFiltersAndSort(SAMPLE_RESULTS, { distance: '5k' });
    expect(results.length).toBe(2);
    expect(results.every(r => r.distanceCanonical === '5k')).toBe(true);
  });

  test('filter by surface', () => {
    const results = applyFiltersAndSort(SAMPLE_RESULTS, { surface: 'trail' });
    expect(results.length).toBe(1);
    expect(results[0].id).toBe('3');
  });

  test('filter by year range', () => {
    const results = applyFiltersAndSort(SAMPLE_RESULTS, { yearFrom: 2023, yearTo: 2023 });
    expect(results.length).toBe(1);
    expect(results[0].id).toBe('2');
  });

  test('view=prs filters to PR only', () => {
    const results = applyFiltersAndSort(SAMPLE_RESULTS, { view: 'prs' });
    expect(results.length).toBe(2);
    expect(results.every(r => r.isPR)).toBe(true);
  });

  test('sort by finishTime asc', () => {
    const results = applyFiltersAndSort(SAMPLE_RESULTS, { sort: 'finishTime', order: 'asc' });
    const times   = results.map(r => r.finishSeconds);
    expect(times).toEqual([...times].sort((a, b) => a - b));
  });
});
