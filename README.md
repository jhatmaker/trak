# Trak

> Track every race. Own your history.

Trak is a personal race history platform for runners. It automatically finds your results across every major timing website, lets you review and accept individual races, and builds a permanent, searchable history of everything you've ever run — with personal records, pace trends, and age-graded scores tracked automatically.

---

## The problem it solves

Your race results are scattered across dozens of timing platforms — Athlinks, RunSignup, NYRR, BAA, your local club's website. There's no single place that owns your history. Results expire, websites change, and you have no control over your own data.

Trak fixes this. It finds your results for you, pulls them into your own local database, and gives you a clean, offline-capable history that you own permanently.

---

## How it works

### 1. Create your profile
Set your name, date of birth, preferred units, and running interests (road, trail, ultra, marathon, parkrun, triathlon, OCR, track, cross country). Your name and DOB are the only things required — they're used to find and match your results on timing sites.

### 2. Automatic discovery
On first save, and on every scheduled background poll, Trak searches a curated list of running result sites for your name:

- **Athlinks** — largest aggregator, searched via their direct API (free, instant, no AI tokens)
- **Ultrasignup** — ultra and trail races
- **RunSignup** — road races across the US
- **NYRR** — New York Road Runners races
- **Boston Athletic Association** — Boston Marathon and BAA events
- **Your custom sources** — any URL you add (local running club, regional timing company, etc.)

Your running interests filter which non-Athlinks sites are searched, so a trail runner doesn't get NYRR results and a road runner doesn't get Ultrasignup noise.

### 3. Review results
When matches are found, they're stored locally as pending matches. The dashboard shows a "Results found" banner. You tap through to the pending matches screen where each discovered race appears as a card with full details — race name, date, distance, location, finish time, overall place, bib number.

**You accept or reject each race individually.** There's no bulk import. This matters because results sites sometimes match the wrong person, especially for common names.

- **Add to profile** — saves the result permanently to your local history, recalculates PRs for that distance
- **Not me** — dismisses the match; it's never shown again

### 4. Your history
Claimed results live in your local Room database, available offline. The dashboard shows your total race count, total distance, unique events, and personal records. The history tab lets you filter and sort by distance, surface, year, or PR status.

### 5. Background polling
Trak checks for new results automatically:

- **Weekly background check** — a cheap existence check that confirms whether any new results appeared (no AI tokens used). Before even making that check, Trak compares the current result count on each site against the last known count — if nothing changed, no network call to the AI is made at all.
- **Manual "Search now"** — triggers a full result extraction immediately. Rate-limited to once per 48 hours. If tapped within the 48-hour window, the search is scheduled and fires automatically at the 48-hour mark — the button shows the scheduled time.
- **Incremental updates** — after the first full discovery, subsequent searches only look for results newer than your last run (`sinceDate`), keeping API costs low.

### 6. Manual add (future)
For sites not in the default list, you can paste a race results URL. The AI (Claude) will find your result on that page and extract the structured data. _This requires the authentication system to be completed — see Pending work below._

---

## Architecture

```
Android App (Java, offline-first)
  └── Room DB — local source of truth
  └── WorkManager — background polls
  └── CredentialManager — Keystore-encrypted site passwords
        │
        │ HTTPS/JSON
        ▼
AWS API Gateway
  └── Lambda (Node.js 20)
        ├── /discover      — public, no auth — searches default sites
        ├── /extract       — JWT required — AI extraction from a URL
        ├── /claims        — JWT required — claim management
        ├── /results       — JWT required — result CRUD
        ├── /profile       — JWT required — profile management
        ├── /sources       — GET public, POST/PUT admin — source catalog
        └── /subscriptions — JWT required — runner ↔ source subscriptions
              │
              ├── Anthropic API (claude-sonnet-4-20250514 + web_search)
              ├── Athlinks direct API (free, no Claude)
              ├── DynamoDB — hot per-user data (results, profile, views)
              └── Aurora PostgreSQL Serverless v2 (via RDS Proxy)
                    └── race_source catalog + runner_source_subscription
```

**Offline-first design**: Everything the user sees comes from Room DB. Network calls update Room in the background. The app is fully functional without internet — it just won't find new results until it reconnects.

**Credential security**: Passwords for credentialed timing sites never leave the device. Only session cookies are sent to Lambda. Passwords are encrypted with Android Keystore.

---

## Current status

### Working
- Profile creation and editing (local Room storage)
- Discovery via `/discover` — Athlinks direct API + Claude web_search for other sites
- Per-result pending matches with full race detail (name, date, distance, finish time, place, bib, location)
- Accept/reject individual pending match results
- Claim saves directly to local Room DB, recalculates PRs — no backend auth required
- Background weekly poll (WorkManager, cheap check)
- Manual "Search now" with 48-hour rate limiting and scheduled-state UI
- Free count pre-check — skips Claude entirely if site result counts haven't changed
- Incremental `sinceDate` filtering on subsequent discovery runs
- Manage Sources — hide/show default sites, add/hide/delete custom sources
- Source GUIDs — stable UUIDs for all default and custom sources
- Dashboard stats and pending match banner
- History list with filter/sort
- Debug build uses 60-second rate limit instead of 48 hours for testing
- Aurora PostgreSQL source catalog (`/sources`, `/subscriptions` endpoints)
- NetworkAwareFragment base class — all fragments react to connectivity changes

### Pending (auth required)
- User registration and login (JWT)
- Manual URL extraction via `/extract` — currently returns 401 (no auth token)
- Backend sync of locally-claimed results (`isSynced=false` rows are queued)
- Multi-device history sync
- BQ gap calculation
- Age-graded performance scores (WMA tables)
- Course certification flag

### Planned
- Parkrun integration (public API)
- Automated poll scheduler (EventBridge cron — off by default to avoid cost)
- Social share card (race result image)
- Coach read-only access
- Club leaderboards
- Strava / Garmin OAuth
- Web frontend (React, same API endpoints)
- Export: CSV, PDF race résumé

---

## Repo structure

```
trak/
├── CLAUDE.md                 ← AI coding assistant instructions
├── SOURCES_REDESIGN.md       ← Aurora source catalog architecture spec
├── README.md                 ← this file
├── .github/workflows/        ← CI/CD (GitHub Actions)
│   ├── backend-ci.yml        ← test + SAM deploy on merge
│   └── android-ci.yml        ← build + test on every PR
├── backend/                  ← AWS Lambda backend (Node.js 20)
│   ├── template.yaml         ← SAM / CloudFormation
│   ├── samconfig.toml        ← deploy config (dev + prod)
│   ├── Makefile              ← SAM layer build
│   ├── functions/            ← discover, extract, claims, results, profile,
│   │                            views, auth, sources, subscriptions
│   ├── shared/
│   │   ├── db/client.js      ← DynamoDB single-table client
│   │   ├── db/postgres.js    ← Aurora PostgreSQL client (IAM auth via RDS Proxy)
│   │   ├── db/migrations/    ← SQL migration files
│   │   └── db/seed/          ← Source catalog seed data
│   └── tests/                ← unit + integration tests
├── android/                  ← Android app (Java, min SDK 26)
│   └── app/src/main/java/com/trackmyraces/trak/
│       ├── data/             ← Room DB, Retrofit, repositories
│       ├── ui/               ← Fragments (extend NetworkAwareFragment), ViewModels
│       ├── sync/             ← PollScheduler, ResultPollWorker (WorkManager)
│       ├── credential/       ← CredentialManager, KeystoreHelper
│       └── util/             ← DistanceNormalizer, AgeGroupCalc, NetworkStateManager
├── docs/                     ← Architecture docs
└── scripts/                  ← oidc-roles.yaml, bootstrap-secrets.sh, gen-token.js
```

---

## Tech stack

| Layer     | Technology |
|-----------|-----------|
| Android   | Java, Room, Retrofit, WorkManager, Android Keystore, Material 3 |
| Backend   | AWS Lambda (Node.js 20), API Gateway, DynamoDB |
| Database  | Aurora PostgreSQL Serverless v2 (via RDS Proxy) |
| AI        | Anthropic claude-sonnet-4-20250514 + web_search tool |
| Secrets   | AWS Secrets Manager (API keys), SSM Parameter Store (VPC config) |
| IaC       | AWS SAM / CloudFormation |
| CI/CD     | GitHub Actions (OIDC — no stored access keys) |

---

## Getting started

### Prerequisites
- Android Studio (for Android development)
- Node.js 20+ and AWS SAM CLI (for backend)
- AWS CLI configured (`aws configure`)
- An Anthropic API key
- An existing AWS VPC with two private subnets in different AZs

### One-time AWS setup (run with admin credentials before first deploy)

#### 1. Create application secrets

```bash
# Anthropic API key
aws secretsmanager create-secret --region us-east-1 \
  --name trak/anthropic-api-key \
  --secret-string '{"apiKey":"sk-ant-YOUR_KEY_HERE"}'

# JWT signing secret
aws secretsmanager create-secret --region us-east-1 \
  --name trak/jwt-secret \
  --secret-string '{"secret":"your-strong-random-secret-here"}'
```

#### 2. Store VPC configuration in SSM Parameter Store

```bash
# Find your VPC and private subnet IDs first:
aws ec2 describe-vpcs --region us-east-1 \
  --query 'Vpcs[*].{Id:VpcId,Name:Tags[?Key==`Name`].Value|[0]}' \
  --output table

aws ec2 describe-subnets --region us-east-1 \
  --query 'Subnets[*].{Id:SubnetId,AZ:AvailabilityZone,Public:MapPublicIpOnLaunch,Name:Tags[?Key==`Name`].Value|[0]}' \
  --output table

# Store the values (pick two private subnets — MapPublicIpOnLaunch=False — in different AZs)
aws ssm put-parameter --region us-east-1 \
  --name /trak/dev/vpc-id \
  --value vpc-YOURVALUE \
  --type String

aws ssm put-parameter --region us-east-1 \
  --name /trak/dev/db-subnet-ids \
  --value "subnet-YOURVALUE1,subnet-YOURVALUE2" \
  --type String
```

#### 3. Deploy CI/CD IAM roles (OIDC — no stored secrets in GitHub)

```bash
aws cloudformation deploy \
  --template-file scripts/oidc-roles.yaml \
  --stack-name trak-github-oidc \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

This creates three scoped roles — no GitHub secrets needed after this:

| Role | Branch | Permissions |
|---|---|---|
| `trak-github-validate` | any | `cloudformation:ValidateTemplate` only |
| `trak-github-deploy-dev` | `develop` | Lambda, DynamoDB, API Gateway, RDS, EC2, Secrets Manager, SSM — dev resources |
| `trak-github-deploy-prod` | `main` | Lambda, DynamoDB, API Gateway, RDS, EC2, Secrets Manager, SSM — prod resources |

> **Re-run this command whenever you update `scripts/oidc-roles.yaml`** — CloudFormation handles the diff.

### Backend setup

```bash
cd backend
npm install

# Build and deploy to dev
sam build && sam deploy --config-env dev

# Run backend tests
npm test
```

### After first deploy — run DB migrations

Aurora takes ~5 minutes to start on first deploy. Once the stack is `UPDATE_COMPLETE`, connect to the Aurora cluster and run the migration:

```bash
# Get the Aurora writer endpoint from CloudFormation outputs
aws cloudformation describe-stacks --stack-name trak-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`AuroraEndpoint`].OutputValue' \
  --output text

# Connect and run migrations (requires network access to Aurora — use a bastion or Cloud9)
psql -h <AURORA_ENDPOINT> -U trakadmin -d trak \
  -f backend/shared/db/migrations/001_initial.sql

psql -h <AURORA_ENDPOINT> -U trakadmin -d trak \
  -f backend/shared/db/seed/sources.sql
```

> For ongoing development, migrations run against the Aurora endpoint directly. A migration runner Lambda is planned for Phase 2.

### Android setup

```bash
# Open android/ in Android Studio — Java project, do not convert to Kotlin

# Set your dev API URL in android/local.properties (create if absent):
echo 'trak.api.url.dev=https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev' \
  >> android/local.properties

# Build debug APK
cd android && ./gradlew assembleDebug

# Install to connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Reset poll timer (testing)

Debug builds use a 60-second rate limit instead of 48 hours. To fully reset all rate-limit timestamps and stored site counts between tests:

```bash
adb shell run-as com.trackmyraces.trak.debug rm \
  /data/user/0/com.trackmyraces.trak.debug/shared_prefs/trak_poll_prefs.xml
```

### Emulator fix (macOS — run after system updates)
```bash
sudo xattr -dr com.apple.quarantine ~/Library/Android/sdk/emulator
sudo xattr -dr com.apple.quarantine ~/Library/Android/sdk/platform-tools
```

---

## Branch strategy

| Branch       | Purpose |
|--------------|---------|
| `main`       | Production-ready. Protected — PRs only. |
| `develop`    | Integration branch. PRs merge here first. |
| `feature/*`  | New features branched from `develop` |
| `fix/*`      | Bug fixes branched from `develop` |
| `hotfix/*`   | Critical production fixes from `main` |

`feature/my-thing` → PR to `develop` → CI → merge → PR `develop` to `main` → deploy

---

## Monetisation model

| Tier | Price | Limits |
|---|---|---|
| Free | $0 | 20 lifetime claims, basic views |
| Pro | $2.99/mo or $19.99/yr | Unlimited claims, all views, export, PR tracking |
| Club | $9.99/mo | Up to 20 members, leaderboards, coach access |
| Lifetime | $29.99 one-time | Everything in Pro, forever |

**Source limits**: Free=5 active sources, Pro=25, Club=unlimited. Enforced in `/subscriptions`.

**Estimated Anthropic API cost per active Pro user/month** (with all cost mitigations):
- Light user (1–2 searches/month): ~$0.51
- Active user (weekly manual searches): ~$1.79
- Break-even: ~10 Pro users covers all fixed AWS costs

---

## Domain

- Web / API: `trackmyraces.com`
- Android package: `com.trackmyraces.trak`
- API base: `https://api.trackmyraces.com` (prod) / `https://dev.api.trackmyraces.com` (dev)
