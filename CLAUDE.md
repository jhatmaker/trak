# Trak — Claude Code Project Instructions

## Project Overview

Trak is a personal race history platform. Runners create a profile, then find and "claim" race results from any public or credentialed race results website. An AI pipeline (AWS Lambda + Anthropic API) extracts structured result data from arbitrary HTML pages. The Android app provides the primary UI with full offline support via Room DB, syncing to a shared AWS backend. A companion web app (optional Phase 2) hits the same API endpoints.

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
│   │   ├── src/main/java/com/trak/
│   │   │   ├── ui/                  ← Activities, Fragments, ViewModels
│   │   │   ├── data/
│   │   │   │   ├── db/              ← Room entities, DAOs, TrakDatabase
│   │   │   │   ├── model/           ← Plain Java model classes
│   │   │   │   ├── repository/      ← Repository pattern classes
│   │   │   │   └── network/         ← Retrofit interfaces, API client
│   │   │   ├── sync/                ← SyncManager, WorkManager workers
│   │   │   ├── credential/          ← CredentialManager, KeystoreHelper
│   │   │   └── util/                ← DistanceNormalizer, AgeGroupCalc, etc.
│   │   └── res/
│   └── gradle.properties            ← Memory settings — do not change
├── backend/                         ← AWS Lambda functions (Node.js)
│   ├── functions/
│   │   ├── extract/                 ← AI extraction Lambda
│   │   ├── results/                 ← CRUD for claims/results
│   │   ├── profile/                 ← User profile Lambda
│   │   └── auth/                    ← Token validation
│   ├── shared/                      ← Shared utilities (distance, age group)
│   ├── template.yaml                ← SAM/CloudFormation template
│   └── package.json
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

| Entity | Purpose |
|---|---|
| `RunnerProfileEntity` | Local copy of the runner's profile |
| `RaceEventEntity` | Deduplicated race events |
| `ResultClaimEntity` | Claim records (pending/confirmed/rejected) |
| `RaceResultEntity` | Full extracted result data |
| `ResultSplitEntity` | Per-km/mile splits |
| `CredentialEntryEntity` | Encrypted credential metadata (NOT the password) |
| `SavedViewEntity` | User-saved filter/sort presets |

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

| Function | Trigger | Purpose |
|---|---|---|
| `extractResult` | POST /extract | AI extraction via Anthropic |
| `claimResult` | POST /claims | Create/confirm a claim |
| `getResults` | GET /results | Fetch runner's result list |
| `getProfile` | GET /profile | Fetch runner profile |
| `updateProfile` | PUT /profile | Update profile |
| `deleteResult` | DELETE /results/{id} | Remove a claimed result |

### Anthropic API Usage

```javascript
// Always use this model string
const MODEL = 'claude-sonnet-4-20250514';

// Always include web_search for extraction
const tools = [{ type: 'web_search_20250305', name: 'web_search' }];

// Extraction prompt structure
const prompt = `
You are extracting a runner's race result from a web page.
Runner: "${runnerName}"${bib ? ` (Bib: ${bib})` : ''}
URL: ${url}
${cookie ? `Use this session cookie: ${cookie}` : ''}

Fetch and read the page, then return ONLY a JSON object:
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
  "splits": [{"label": string, "distance_meters": number, "elapsed_seconds": number}],
  "source_url": string,
  "notes": string|null
}
Return ONLY the JSON, no markdown, no explanation.
`;
```

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

### POST /claims
Request: `{ extractionId, runnerId, edits: {} }` — `edits` contains any runner corrections
Response: `{ claimId, resultId, isPR, ageGroup, ageAtRace }`

### GET /results?runnerId=&view=&filter=&sort=
Supported views: `all`, `prs`, `byyear`, `byrace`, `bycanonical`
Filter params: `distance`, `surface`, `yearFrom`, `yearTo`, `raceNameSlug`
Sort params: `date`, `distance`, `finishTime`, `overallPlace`, `ageGroupPlace`

---

## Features Not Yet Implemented (Backlog)

- [ ] Parkrun integration (public API — no auth needed)
- [ ] Boston Qualifier (BQ) tracking and gap calculation
- [ ] Age-graded performance score (WMA tables)
- [ ] Course certification flag (USATF/IAAF certified)
- [ ] Push notifications for new results on watched URLs
- [ ] Social share card generation (race result image for Instagram)
- [ ] Coach access (read-only share link with expiry)
- [ ] Club leaderboard (multi-user, opt-in)
- [ ] Virtual race support (Strava segment results)
- [ ] OAuth — Strava, Garmin Connect
- [ ] Playwright Lambda for JS-heavy login portals (Phase 3)
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
- Anthropic API: ~$0.10–0.30 (5–15 extraction runs × $0.02–0.05 each)
- Break-even: ~10 Pro users covers all fixed costs

---

## Development Workflow

```bash
# Android — build and install
cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Backend — local test with SAM
cd backend && sam local invoke extractResult --event events/extract-test.json

# Backend — deploy
cd backend && sam deploy --guided

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
