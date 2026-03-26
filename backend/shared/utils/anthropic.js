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
    `${i + 1}. ${s.name} — ${s.searchUrl}`
  ).join('\n');

  return `You are helping a runner discover whether they have results on popular running websites.

Runner to search for: "${runnerName}"${ageNote}

Search each of these websites and determine if a runner named "${runnerName}" has results there.
For each site, use the provided search URL, then look for a profile or results page specifically for this runner.
If you find multiple people with the same name, use the approximate age to pick the most likely match.

Sites to search:
${siteList}

For each site, return:
- Whether the runner was found
- The direct URL to their results or profile page (not the search page)
- An approximate count of results if visible
- Brief notes to confirm it's the right person (age, location, etc.)

Return ONLY a valid JSON array — no markdown, no explanation:

[
  {
    "siteId": "athlinks",
    "found": true,
    "resultsUrl": "https://www.athlinks.com/athletes/123456789/results",
    "resultCount": 47,
    "notes": "Jane Smith, age 38, Boston MA — 47 race results"
  },
  {
    "siteId": "ultrasignup",
    "found": false,
    "resultsUrl": null,
    "resultCount": 0,
    "notes": null
  }
]

Rules:
- Return one object per site in the same order they were listed above.
- If the runner is NOT found on a site, set found: false, resultsUrl: null, resultCount: 0.
- resultsUrl must point to the runner's results/profile page, NOT the search results page.
- resultCount is approximate — 0 if unknown.
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
    max_tokens: 2000,
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

  console.log(JSON.stringify({
    type:         'ANTHROPIC_USAGE',
    operation:    'discover',
    inputTokens:  message.usage?.input_tokens,
    outputTokens: message.usage?.output_tokens,
    runnerName:   params.runnerName,
    sitesSearched: params.sites.length,
    sitesFound:   Array.isArray(parsed) ? parsed.filter(s => s.found).length : 0,
  }));

  return parsed;
}

module.exports = { extractRaceResult, discoverRunner };
