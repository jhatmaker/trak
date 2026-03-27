# Trak — Claude Code Project Instructions

## Project Overview

Trak is a personal race history platform for runners. The goal is to give every runner a single, permanent, offline-capable record of every race they've ever run — without requiring them to manually enter anything.

### The intended workflow

1. **Profile setup** — runner enters name, DOB, interests (road/trail/ultra/marathon/etc.), preferred units. Name + DOB are the only required fields; they're what timing sites use to identify a runner.

2. **Automatic discovery** — on first save and on every subsequent background poll, the app searches a curated list of running result sites for the runner's name. Athlinks is called via a direct free API (no AI tokens). Other sites (Ultrasignup, RunSignup, NYRR, BAA) are searched via Claude's web_search tool. The runner's interests filter which non-Athlinks sites are searched. User-added custom sources (local club sites, regional timers) are included in the search list.

3. **Per-result pending matches** — every individual race found during discovery is stored as a PendingMatchEntity in Room with full detail: race name, date, distance, finish time, place, bib, location. The dashboard shows a banner with the count of pending matches waiting for review.

4. **Review and claim** — the runner sees each discovered race as a card with all details. They tap "Add to profile" to accept it or "Not me" to dismiss it. Accepting a result creates a RaceResultEntity directly from the pending match data — no API call, fully offline. PRs are recalculated for that distance immediately.

5. **Background polling** — WorkManager runs a cheap weekly check. Before making any AI call, the app compares stored result counts per site (from last full extraction) against the current count (free Athlinks API call). If counts are unchanged, the entire check is skipped with noChange=true. Manual "Search now" is rate-limited to once per 48 hours; a second tap within the window schedules the search for the 48-hour mark and shows the scheduled time in the UI.

6. **Manual URL add** (not yet available — auth required) — for races not on default sites, the runner pastes a results page URL. The AI (Claude) extracts the structured result. This requires JWT authentication, which is not yet implemented.

### Auth status (important for development)

The `/discover` endpoint is public — no auth required. All other Lambda endpoints (`/extract`, `/claims`, `/results`, `/profile`) require a JWT Bearer token. There is currently no login/registration flow in the Android app, so `auth_token` in the profile is always null. This means:
- Discovery and local claim flow work fully
- Manual URL extraction always returns 401
- Locally claimed results have `isSynced=false` and are queued for backend sync once auth is added

---

## Architecture Summary

```
Android App (Java)
  └── UI (Activities/Fragments/RecyclerView)
  └── Room DB — local cache, offline-first
  └── SyncManager — online/offline routing
  └── WorkManager — background sync
  └── CredentialManager — Android Keystore encryption
        │
        │ HTTPS/JSON  (session cookie only — never raw credentials)
        ▼
AWS API Gateway
  └── Lambda (Node.js 20.x)
        ├── Secrets Manager — Anthropic API key
        ├── Anthropic API (claude-sonnet-4-20250514 + web_search tool)
        └── DynamoDB — result cache + user data
              │
              ▼
        Race result websites (public + credentialed)
```

---

## Repository Structure

```
trak/
├── CLAUDE.md                        ← this file
├── android/                         ← Android Studio project (Java)
│   ├── app/
│   │   ├── src/main/java/com/trackmyraces/trak/
│   │   │   ├── ui/                  ← Activities, Fragments, ViewModels
│   │   │   ├── data/
│   │   │   │   ├── db/              ← Room entities, DAOs, TrakDatabase
│   │   │   │   ├── model/           ← Plain Java model classes
│   │   │   │   ├── repository/      ← Repository pattern classes
│   │   │   │   └── network/         ← Retrofit interfaces, API client
│   │   │   ├── sync/                ← SyncManager, PollScheduler, WorkManager workers
│   │   │   ├── credential/          ← CredentialManager, KeystoreHelper
│   │   │   └── util/                ← DistanceNormalizer, AgeGroupCalc, etc.
│   │   └── res/
│   └── gradle.properties            ← Memory settings — do not change
├── backend/                         ← AWS Lambda functions (Node.js)
│   ├── functions/
│   │   ├── extract/                 ← AI extraction Lambda
│   │   ├── discover/                ← Search default sites for a runner (public, no auth)
│   │   ├── claims/                  ← Claim management
│   │   ├── results/                 ← Result CRUD
│   │   ├── profile/                 ← User profile Lambda
│   │   ├── views/                   ← Saved view presets
│   │   └── auth/                    ← Token validation
│   ├── shared/                      ← Shared utilities (db, raceLogic, response, anthropic)
│   │   └── package.json             ← @anthropic-ai/sdk dependency for the Lambda layer
│   ├── Makefile                     ← SAM layer build target (build-SharedLayer)
│   ├── template.yaml                ← SAM/CloudFormation template
│   ├── samconfig.toml               ← Deploy config (dev + prod)
│   └── package.json                 ← Root deps, dev tools, test runner
├── docs/
│   ├── architecture.docx            ← Full architecture document
│   ├── api-contract.md              ← Lambda API request/response spec
│   └── data-model.md                ← DB schema reference
└── scripts/
    └── sync_changed.sh              ← Version sync utility
```

---

## Android Project — Key Details

### Build Environment
- **Language:** Java (not Kotlin)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35
- **IDE:** Android Studio on Intel Mac
- **Gradle:** 9.3.1

### gradle.properties — DO NOT MODIFY these memory settings
```properties
org.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=512m
org.gradle.parallel=false
org.gradle.workers.max=2
android.useAndroidX=true
```

### Key Dependencies (app/build.gradle)
```groovy
// Room DB
implementation 'androidx.room:room-runtime:2.6.1'
annotationProcessor 'androidx.room:room-compiler:2.6.1'

// WorkManager
implementation 'androidx.work:work-runtime:2.9.0'

// Retrofit + OkHttp
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'

// ViewModel + LiveData
implementation 'androidx.lifecycle:lifecycle-viewmodel:2.7.0'
implementation 'androidx.lifecycle:lifecycle-livedata:2.7.0'

// RecyclerView, CardView, Material
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.cardview:cardview:1.0.0'
implementation 'com.google.android.material:material:1.11.0'

// MPAndroidChart (for trend charts)
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
```

### Room DB Entities

Current schema version: **8**

| Entity | Purpose |
|---|---|
| `RunnerProfileEntity` | Local copy of the runner's profile (name, dob, gender, units, tempUnit, interests, lastDiscoverAt, pendingCount) |
| `RaceEventEntity` | Deduplicated race events |
| `ResultClaimEntity` | Claim records (pending/confirmed/rejected) |
| `RaceResultEntity` | Full extracted result data (includes `elevationStartMeters`) |
| `ResultSplitEntity` | Per-km/mile splits |
| `CredentialEntryEntity` | Encrypted credential metadata (NOT the password) |
| `SavedViewEntity` | User-saved filter/sort presets |
| `PendingMatchEntity` | Discovered sites waiting for runner to confirm/dismiss (dedup key UNIQUE) |
| `UserSitePrefEntity` | Per-user source preferences: hide flag + custom source URLs |

Room migration history:
- 1→2: initial schema
- 2→3: added `preferred_units` to `runner_profile`
- 3→4: added `interests` to `runner_profile`
- 4→5: added `elevation_start_meters` to `race_result`
- 5→6: added `preferred_temp_unit` to `runner_profile`
- 6→7: added `pending_match` table; added `last_discover_at` + `pending_count` to `runner_profile`
- 7→8: added `user_site_pref` table (per-user hide flags + custom sources)

Distance and temperature preferences are separate fields:
- `preferredUnits` — `"imperial"` (miles) or `"metric"` (km). Default: `"imperial"`
- `preferredTempUnit` — `"fahrenheit"` or `"celsius"`. Default: `"fahrenheit"`

### Offline-First Pattern

```java
// Always read from Room first, then sync in background
public LiveData<List<RaceResultEntity>> getResults(String runnerId) {
    // 1. Return Room data immediately (observed by UI)
    LiveData<List<RaceResultEntity>> local = dao.getResultsForRunner(runnerId);
    // 2. Trigger background sync if online
    syncManager.syncIfOnline(runnerId);
    return local;
}
```

### CredentialManager Pattern

Credentials NEVER leave the device and NEVER go to Lambda. Only session cookies are sent.

```java
// Store: encrypt password into Android Keystore
keystoreHelper.encryptAndStore(siteUrl, username, password);

// Use: decrypt → POST to site → get cookie → pass cookie to Lambda
String cookie = siteLoginClient.login(siteUrl, username, keystoreHelper.decrypt(siteUrl));
lambdaClient.extractWithCookie(url, cookie, runnerName);

// Lambda receives: { url, cookie, runnerName } — no password
```

---

## Backend (AWS Lambda) — Key Details

### Runtime & Config
- **Runtime:** Node.js 20.x
- **Memory:** 512MB (1024MB for Playwright functions)
- **Timeout:** 60s (extraction can take 15–20s)
- **Region:** us-east-1

### Lambda Functions

| Function | Trigger | Auth | Purpose |
|---|---|---|---|
| `extractResult` | POST /extract | JWT | AI extraction via Anthropic |
| `discoverResult` | POST /discover | None | Search default sites for a runner |
| `claimResult` | POST /claims | JWT | Create/confirm a claim |
| `getResults` | GET /results | JWT | Fetch runner's result list |
| `getProfile` | GET /profile | JWT | Fetch runner profile |
| `updateProfile` | PUT /profile | JWT | Update profile |
| `deleteResult` | DELETE /results/{id} | JWT | Remove a claimed result |

### SharedLayer

The layer is built via `BuildMethod: makefile` using `backend/Makefile`. This ensures:
- Files land at `/opt/nodejs/shared/...` matching all `require('/opt/nodejs/shared/...')` calls
- `@anthropic-ai/sdk` is bundled (not in the Lambda Node.js 20 runtime)
- `config/defaultSites.js` and all shared files are included reliably

`backend/shared/package.json` declares `@anthropic-ai/sdk` as a dependency. The layer build runs `npm install` in the layer artifact directory. `@aws-sdk/*` is available from the Lambda runtime and does not need bundling.

### Anthropic API Usage

```javascript
// Always use this model string
const MODEL = 'claude-sonnet-4-20250514';

// Always include web_search for extraction
const tools = [{ type: 'web_search_20250305', name: 'web_search' }];

// Extraction prompt structure — returns this JSON schema:
{
  "found": boolean,
  "race_name": string,
  "race_date": "YYYY-MM-DD",
  "distance_label": string,
  "distance_meters": number|null,
  "bib_number": string|null,
  "finish_time": "H:MM:SS",
  "finish_seconds": number,
  "chip_time": "H:MM:SS"|null,
  "chip_seconds": number|null,
  "overall_place": number|null,
  "overall_total": number|null,
  "gender_place": number|null,
  "gender_total": number|null,
  "age_group_label": string|null,
  "age_group_place": number|null,
  "age_group_total": number|null,
  "pace_per_km_seconds": number|null,
  "elevation_start_meters": number|null,   // start line elevation from geocoding
  "splits": [{"label": string, "distance_meters": number, "elapsed_seconds": number}],
  "source_url": string,
  "notes": string|null
}
// Return ONLY the JSON, no markdown, no explanation.
```

`elevation_start_meters` is fetched from the Open-Meteo geocoding API (free, no key needed) using the race location — the same API call already made to get weather data returns `elevation` in the response at zero extra cost.

### DynamoDB Table Design

```
Table: trak-prod

PK                          SK                          Attributes
RUNNER#{runnerId}           PROFILE                     name, dob, gender, ...
RUNNER#{runnerId}           CLAIM#{claimId}             status, raceEventId, ...
RUNNER#{runnerId}           RESULT#{resultId}           finishSeconds, place, ...
RUNNER#{runnerId}           CRED#{credId}               siteUrl, username (NO password)
RACE#{raceEventId}          META                        name, date, distance, ...
RACE#{raceEventId}          CLAIM#{claimId}             runnerId, status
```

GSI: `byRaceEvent` — PK: `raceEventId`, SK: `claimId` (for multi-runner club views)

---

## Core Business Logic

### Distance Normalisation

Canonical distances (stored as meters). Always normalise before PR comparison.

```javascript
const CANONICAL_DISTANCES = {
  '1mile':    1609,
  '5k':       5000,
  '8k':       8047,
  '10k':      10000,
  '15k':      15000,
  '10mile':   16093,
  'halfmarathon': 21097,
  '25k':      25000,
  '30k':      30000,
  'marathon': 42195,
  '50k':      50000,
  '50mile':   80467,
  '100k':     100000,
  '100mile':  160934,
};

// Normalise extracted distance_label → canonical key
function normaliseDistance(label, meters) {
  // Try meters first if provided
  // Then fuzzy match on label
  // Tolerance: ±3% for certified courses, ±8% for trail
}
```

### Age Group Calculation

```javascript
function calcAgeAtRace(dob, raceDate) {
  const race = new Date(raceDate);
  const birth = new Date(dob);
  let age = race.getFullYear() - birth.getFullYear();
  const bdayThisYear = new Date(race.getFullYear(), birth.getMonth(), birth.getDate());
  if (race < bdayThisYear) age--;
  return age;
}

function calcAgeGroup(age) {
  // Standard WAVA 5-year brackets
  const lower = Math.floor(age / 5) * 5;
  return `${lower}-${lower + 4}`;  // e.g. "40-44"
}
```

### PR Detection

PR is recalculated on every new claim for that canonical distance. Chip time preferred over gun time.

```javascript
async function recalculatePR(runnerId, canonicalDistance) {
  const results = await getResultsByDistance(runnerId, canonicalDistance);
  const sorted = results.sort((a, b) => (a.chipSeconds || a.finishSeconds) - (b.chipSeconds || b.finishSeconds));
  for (let i = 0; i < sorted.length; i++) {
    await updateIsPR(sorted[i].resultId, i === 0);
  }
}
```

### Race Name Canonicalization

```javascript
function canonicaliseName(rawName) {
  return rawName
    .toLowerCase()
    .replace(/\b(20\d{2})\b/g, '')        // strip years
    .replace(/presented by .+/gi, '')       // strip sponsors
    .replace(/[^a-z0-9 ]/g, '')            // strip punctuation
    .replace(/\s+/g, ' ')
    .trim();
}
// "2024 Boston Marathon Presented by Bank of America" → "boston marathon"
```

---

## API Contract Summary

### POST /discover (public — no auth)
Request:
```json
{
  "runnerName": "Jane Smith",
  "dateOfBirth": "1985-04-12",
  "interests": ["road", "marathon"]
}
```
Response: `{ sites: [{ siteId, siteName, found, resultsUrl, resultCount, notes }] }`

`interests` filters which sites are searched (road, trail, ultra, marathon, parkrun, triathlon, ocr, track, crosscountry). Sites tagged `always` (e.g. Athlinks) are searched regardless.

### POST /extract
Request:
```json
{
  "runnerName": "Jane Smith",
  "bibNumber": "1042",
  "url": "https://results.example.com/race/2024",
  "cookie": "session=abc123",
  "extraContext": "female, age group 40-44"
}
```
Response: extracted result JSON (see Anthropic prompt above) + `extractionId`

Bib number is per-race, not stored on the profile. It is passed as a hint to Claude for result lookup only.

### POST /claims
Request: `{ extractionId, runnerId, edits: {} }` — `edits` contains any runner corrections
Response: `{ claimId, resultId, isPR, ageGroup, ageAtRace }`

### GET /results?runnerId=&view=&filter=&sort=
Supported views: `all`, `prs`, `byyear`, `byrace`, `bycanonical`
Filter params: `distance`, `surface`, `yearFrom`, `yearTo`, `raceNameSlug`
Sort params: `date`, `distance`, `finishTime`, `overallPlace`, `ageGroupPlace`

---

## Implemented Features (reference)

### Core flow
- [x] Profile setup — name, DOB, gender, distance units (miles/km), temperature units (°F/°C), running interests
- [x] Discovery via `/discover` — Athlinks direct API + Claude web_search for other default sites
- [x] Interests filter — which non-Athlinks sites are included in a search (road, trail, ultra, marathon, parkrun, triathlon, ocr, track, crosscountry)
- [x] Per-result pending matches — each discovered race stored individually with full detail (name, date, distance, finish time, place, bib, location)
- [x] Accept/reject per-result — "Add to profile" creates a local `RaceResultEntity` without any API call; "Not me" dismisses permanently
- [x] Local PR recalculation on claim — chip time preferred over gun time, ±5% distance tolerance
- [x] Dashboard pending-matches banner with live count

### Polling and cost controls
- [x] Background weekly poll — `ResultPollWorker` via WorkManager periodic work (cheap check, no individual result extraction)
- [x] Free count pre-check — stores `site_count_{siteId}` in SharedPrefs; skips Claude entirely when counts are unchanged
- [x] 48-hour rate limit on full extraction — `last_extract_at_ms` in SharedPrefs
- [x] Scheduled-state UI — "Search scheduled · runs at HH:mm" when within rate-limit window; auto-resets via WorkInfo LiveData
- [x] Incremental `sinceDate` filter — subsequent searches only request results newer than last discovery run
- [x] 50-result cap on first full discovery, newest-first
- [x] Debug build uses 60-second rate limit instead of 48 hours

### Source management
- [x] Manage Sources screen — hide/show default sites, add/hide/delete custom sources
- [x] Custom sources — user can add any URL (local club, regional timer); appears in the search count
- [x] Source deduplication — adding a known domain suggests using the existing default entry instead
- [x] Enabled source count drives the "Search all (N) enabled sources now" button label (defaults + custom)

### Data
- [x] Room DB migrations 1–9
- [x] Offline-first — all reads from Room; network updates Room in background
- [x] Elevation at race start — `elevationStartMeters` from Open-Meteo geocoding
- [x] `isSynced=false` flag on locally-claimed results — queued for backend sync once auth is added

## Features Not Yet Implemented (Backlog)

### Blocked on auth
- [ ] User registration / login (JWT)
- [ ] Manual URL extraction via `/extract` — returns 401 until auth exists
- [ ] Backend sync of locally-claimed results
- [ ] Multi-device history

### Discovery
- [ ] Custom sources actually searched during discovery — currently added to count but not passed to `/discover`
- [ ] Parkrun integration (public API — no auth needed)

### Race data
- [ ] Boston Qualifier (BQ) tracking and gap calculation
- [ ] Age-graded performance score (WMA tables)
- [ ] Course certification flag (USATF/IAAF certified)

### Social / sharing
- [ ] Social share card generation (race result image)
- [ ] Coach read-only access (share link with expiry)
- [ ] Club leaderboard (multi-user, opt-in)

### Platform
- [ ] OAuth — Strava, Garmin Connect, virtual race support
- [ ] Playwright Lambda for JS-heavy login portals
- [ ] Web frontend (React — same API endpoints)

---

## Monetisation

### Tiers
| Tier | Price | Limits |
|---|---|---|
| Free | $0 | 20 lifetime claims, basic views |
| Pro | $2.99/mo or $19.99/yr | Unlimited claims, all views, export, PR tracking |
| Club | $9.99/mo | Up to 20 members, leaderboards, coach access |
| Lifetime | $29.99 one-time | Everything in Pro, forever |

### Cost Model (per active Pro user/month)
- Lambda + DynamoDB: ~$0.00 (free tier covers it)
- Anthropic API (with all 4 cost mitigations): ~$0.51 light / ~$1.79 active user
  - Mitigations: free count pre-check, 48h rate limit, sinceDate incremental filter, 50-result cap, background = cheap check only
- Without mitigations (naive extraction on every poll): ~$15–20/month — unsustainable
- Break-even: ~10 Pro users covers all fixed costs

---

## API Versioning Strategy

API versioning is currently **not needed** and is intentionally absent. Here's why, and what to do when it eventually matters:

**Why it's fine now:**
- API Gateway exposes fixed paths (`/extract`, `/discover`, etc.) — deploying a new Lambda doesn't change those paths
- All schema changes so far are additive (new fields). Old app versions ignore unknown fields; new app versions reading a missing field get `null`
- We control both sides and can coordinate deploys with app releases

**Safe change pattern (deploy order matters):**
1. Deploy Lambda first (backward compatible — new field returned, old app ignores it)
2. Ship Android app update (reads new field)

**When you need versioning (breaking changes — rename/remove a field or endpoint):**
- Keep old endpoint alive while new one exists, deprecate after all users have updated
- Use an `X-App-Version` request header if Lambda needs to behave differently per client version
- Use API Gateway stages (`/v1/`, `/v2/`) only for true breaking rewrites

## Development Workflow

```bash
# Android — build and install
cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Backend — local test with SAM
cd backend && sam local invoke ExtractFunction --event events/extract-test.json

# Backend — build (always full rebuild — cached=true removed from samconfig)
cd backend && sam build

# Backend — deploy to dev
cd backend && sam deploy --config-env dev --no-confirm-changeset

# Run backend tests
cd backend && npm test
```

## CI/CD — GitHub Actions OIDC Setup (one-time per AWS account)

GitHub Actions authenticates to AWS via OIDC — no access keys are stored in GitHub.
Run once with personal admin credentials before the first push to a new environment:

```bash
aws cloudformation deploy \
  --template-file scripts/oidc-roles.yaml \
  --stack-name trak-github-oidc \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

**What it creates** (`scripts/oidc-roles.yaml`):

| Role | Allowed branch | Scope |
|---|---|---|
| `trak-github-validate` | any | `cloudformation:ValidateTemplate` only |
| `trak-github-deploy-dev` | `develop` | Lambda, DynamoDB, API Gateway, IAM — `trak-dev-*` resources |
| `trak-github-deploy-prod` | `main` | Lambda, DynamoDB, API Gateway, IAM — `trak-prod-*` resources |

**To update role permissions:** edit `scripts/oidc-roles.yaml` and re-run the same
`aws cloudformation deploy` command. CloudFormation handles the diff.

**Prerequisites for the deploy command:**
- AWS CLI configured (`aws configure`) with an IAM identity that has
  `iam:CreateRole`, `iam:PutRolePolicy`, and `iam:CreateOpenIDConnectProvider`

## Emulator Fix (macOS — run if emulator fails after system update)
```bash
sudo xattr -dr com.apple.quarantine ~/Library/Android/sdk/emulator
sudo xattr -dr com.apple.quarantine ~/Library/Android/sdk/platform-tools
```

---

## Coding Conventions

- **Android:** Standard Java naming. ViewModels for all UI state. Repository pattern for data access. Never do network or DB calls on the main thread.
- **Backend:** Async/await throughout. No raw `console.log` in production — use structured logging. All Lambda handlers wrapped in try/catch with consistent error response shape: `{ error: string, code: string }`.
- **Database:** All DynamoDB writes go through a `dbClient` wrapper that adds `updatedAt` automatically. Never delete records — use `status: 'deleted'` soft-delete pattern.
- **Secrets:** Anthropic API key in AWS Secrets Manager only. Never in env vars, never in code, never in logs.
- **Testing:** Unit tests for all pure functions (distance normalise, age calc, PR logic, name canonicalise). Integration tests for Lambda handlers using local DynamoDB.
