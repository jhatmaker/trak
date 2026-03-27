'use strict';

/**
 * shared/utils/anthropic.js
 * Anthropic API client — fetches key from Secrets Manager, runs extraction.
 */

const { SecretsManagerClient, GetSecretValueCommand } = require('@aws-sdk/client-secrets-manager');
const Anthropic = require('@anthropic-ai/sdk');

const smClient = new SecretsManagerClient({ region: process.env.AWS_REGION || 'us-east-1' });

// Module-level cache — survive warm Lambda invocations
let _anthropicClient = null;
let _secretFetchedAt = 0;
const SECRET_TTL_MS  = 5 * 60 * 1000; // re-fetch key every 5 minutes

async function getClient() {
  const now = Date.now();
  if (_anthropicClient && (now - _secretFetchedAt) < SECRET_TTL_MS) return _anthropicClient;

  const { SecretString } = await smClient.send(new GetSecretValueCommand({
    SecretId: process.env.ANTHROPIC_SECRET_NAME,
  }));

  const secret = JSON.parse(SecretString);
  _anthropicClient = new Anthropic({ apiKey: secret.apiKey });
  _secretFetchedAt = now;
  return _anthropicClient;
}

// ─── Extraction prompt ────────────────────────────────────────────────────────

function buildExtractionPrompt({ runnerName, bibNumber, url, cookie, extraContext }) {
  const cookieInstruction = cookie
    ? `\n\nIMPORTANT: This page requires authentication. Use this session cookie when fetching:\n${cookie}`
    : '';

  const contextNote = extraContext
    ? `\n\nExtra context to help identify the runner: ${extraContext}`
    : '';

  return `You are extracting a runner's race result from a web page.

Runner to find: "${runnerName}"${bibNumber ? ` (Bib/ID: ${bibNumber})` : ''}
Source URL: ${url}${cookieInstruction}${contextNote}

Fetch and read this page, then extract ALL race result data for this specific runner.

Return ONLY a valid JSON object — no markdown, no explanation, no preamble:

{
  "found": true or false,
  "race_name": "Full race name as shown on the page",
  "race_date": "YYYY-MM-DD",
  "race_city": "City name or null",
  "race_state": "State/province or null",
  "race_country": "Country or null",
  "distance_label": "Distance as shown e.g. 'Half Marathon', '10K', '50 Mile'",
  "distance_meters": number or null,
  "surface_type": "road" | "trail" | "track" | "xc" | "mixed" | null,
  "bib_number": "Bib as string or null",
  "finish_time": "H:MM:SS or MM:SS",
  "finish_seconds": integer,
  "chip_time": "H:MM:SS or null",
  "chip_seconds": integer or null,
  "overall_place": integer or null,
  "overall_total": integer or null,
  "gender": "M" | "F" | "NB" | null,
  "gender_place": integer or null,
  "gender_total": integer or null,
  "age_group_label": "Age group as shown e.g. 'M40-44', 'F35-39' or null",
  "age_group_place": integer or null,
  "age_group_total": integer or null,
  "splits": [
    {
      "label": "Split name e.g. '5K', 'Mile 13', 'Halfway'",
      "distance_meters": number,
      "elapsed_seconds": integer,
      "split_seconds": integer or null,
      "split_place": integer or null
    }
  ],
  "elevation_gain_meters": integer or null,
  "elevation_start_meters": integer or null,
  "temperature_celsius": number or null,
  "weather_condition": "Clear" | "Partly cloudy" | "Overcast" | "Drizzle" | "Rain" | "Snow" | "Fog" | "Thunderstorm" | null,
  "is_certified": true | false | null,
  "source_url": "${url}",
  "extraction_notes": "Any caveats, ambiguities, or quality notes, or null"
}

Rules:
- If the runner is NOT found on this page, return { "found": false } and nothing else.
- finish_seconds must be an integer (e.g. 1:23:45 = 5025).
- If chip time is unavailable, chip_time and chip_seconds should be null.
- splits array may be empty [] if no split data is available.
- Do NOT invent data — only return values clearly present on the page.
- Return ONLY the JSON object. No markdown fences. No explanation.`;
}

// ─── Main extraction function ─────────────────────────────────────────────────

/**
 * Call Anthropic to extract a race result from a URL.
 * @param {Object} params
 * @param {string} params.runnerName
 * @param {string} [params.bibNumber]
 * @param {string} params.url
 * @param {string} [params.cookie]     - Session cookie for credentialed sites
 * @param {string} [params.extraContext]
 * @returns {Object} Parsed extraction result
 */
async function extractRaceResult(params) {
  const client = await getClient();
  const prompt = buildExtractionPrompt(params);

  const message = await client.messages.create({
    model:      'claude-sonnet-4-20250514',
    max_tokens: 2000,
    tools: [{
      type: 'web_search_20250305',
      name: 'web_search',
    }],
    messages: [{ role: 'user', content: prompt }],
  });

  // Find the final text block (after any tool use)
  const textBlocks = message.content.filter(b => b.type === 'text');
  if (!textBlocks.length) {
    throw new Error('Anthropic returned no text content');
  }

  const rawText = textBlocks[textBlocks.length - 1].text.trim();

  // Strip any accidental markdown fences
  const jsonText = rawText.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim();

  let parsed;
  try {
    parsed = JSON.parse(jsonText);
  } catch {
    throw new Error(`Anthropic response was not valid JSON: ${jsonText.slice(0, 200)}`);
  }

  // Log usage for cost tracking (redact cookie)
  console.log(JSON.stringify({
    type:       'ANTHROPIC_USAGE',
    inputTokens:  message.usage?.input_tokens,
    outputTokens: message.usage?.output_tokens,
    found:        parsed.found,
    url:          params.url,
  }));

  return parsed;
}

// ─── Discovery prompt ─────────────────────────────────────────────────────────

function buildDiscoveryPrompt({ runnerName, approximateAge, sites }) {
  const ageNote = approximateAge
    ? ` (approximate age: ${approximateAge})`
    : '';

  const siteList = sites.map((s, i) =>
    `${i + 1}. ${s.name} — search query: ${s.searchQuery}`
  ).join('\n');

  return `You are helping a runner find their complete race history on popular running websites.

Runner: "${runnerName}"${ageNote}

For each site listed below, call web_search with the provided query. If the runner is found,
navigate to their athlete profile page and extract ALL individual race results listed there.
If multiple people share the same name, use the approximate age to select the correct person.

Search each site now:
${siteList}

For each site, return an object. If the runner was found, populate the "results" array with
one object per race result listed on their profile page.

Return ONLY a valid JSON array — no markdown, no explanation:

[
  {
    "siteId": "ultrasignup",
    "found": true,
    "athleteUrl": "https://ultrasignup.com/runner/jane-smith-12345",
    "results": [
      {
        "resultId": "ultrasignup-event-67890-runner-12345",
        "raceName": "2024 Vermont 100",
        "raceDate": "2024-07-20",
        "distanceLabel": "100 Mile",
        "distanceMeters": 160934,
        "location": "South Woodstock, VT",
        "bibNumber": "123",
        "finishTime": "23:45:30",
        "finishSeconds": 85530,
        "overallPlace": 45,
        "overallTotal": 312,
        "resultsUrl": "https://ultrasignup.com/results_event.aspx?did=12345&id=67890"
      }
    ],
    "notes": null
  },
  {
    "siteId": "runsignup",
    "found": false,
    "athleteUrl": null,
    "results": [],
    "notes": null
  }
]

Rules:
- Return one object per site in the same order they were listed above.
- If the runner is NOT found, set found: false, results: [], athleteUrl: null.
- athleteUrl is the runner's profile/results page — NOT the search results page.
- results array must contain one entry per individual race result on that page.
  Include as many results as are visible — do not truncate.
- resultId should be a stable unique identifier for this specific result; use a combination
  of site IDs and race/event IDs if visible; null if no stable ID is available.
- finishSeconds must be an integer (e.g. 3:45:22 = 13522).
- distanceMeters: standard values — 5K=5000, 10K=10000, HM=21097, Marathon=42195, 50K=50000.
  Use 0 if truly unknown.
- resultsUrl should point to this specific result entry, not the athlete profile page.
  If only the athlete page is available, use that URL.
- Return ONLY the JSON array. No markdown fences. No explanation.`;
}

/**
 * Search multiple running result sites for a runner.
 * @param {Object} params
 * @param {string} params.runnerName
 * @param {number} [params.approximateAge]
 * @param {Array}  params.sites  — from resolveSiteUrls()
 * @returns {Array} Parsed discovery result array
 */
async function discoverRunner(params) {
  const client = await getClient();
  const prompt = buildDiscoveryPrompt(params);

  const message = await client.messages.create({
    model:      'claude-sonnet-4-20250514',
    max_tokens: 8000,   // individual results per site can be many rows
    tools: [{
      type: 'web_search_20250305',
      name: 'web_search',
    }],
    messages: [{ role: 'user', content: prompt }],
  });

  const textBlocks = message.content.filter(b => b.type === 'text');
  if (!textBlocks.length) throw new Error('Anthropic returned no text content');

  const rawText  = textBlocks[textBlocks.length - 1].text.trim();
  const jsonText = rawText.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim();

  let parsed;
  try {
    parsed = JSON.parse(jsonText);
  } catch {
    throw new Error(`Discovery response was not valid JSON: ${jsonText.slice(0, 200)}`);
  }

  const totalResults = Array.isArray(parsed)
    ? parsed.reduce((n, s) => n + (Array.isArray(s.results) ? s.results.length : 0), 0)
    : 0;

  console.log(JSON.stringify({
    type:          'ANTHROPIC_USAGE',
    operation:     'discover',
    inputTokens:   message.usage?.input_tokens,
    outputTokens:  message.usage?.output_tokens,
    runnerName:    params.runnerName,
    sitesSearched: params.sites.length,
    sitesFound:    Array.isArray(parsed) ? parsed.filter(s => s.found).length : 0,
    totalResults,
  }));

  return parsed;
}

module.exports = { extractRaceResult, discoverRunner };
