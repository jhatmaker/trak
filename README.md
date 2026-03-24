# Trak

> Track every race. Own your history.

Trak is a personal race history platform for runners. It uses AI (Anthropic claude-sonnet) to extract and parse race results from any timing website, letting runners build a complete, searchable history across every race they've ever run — public or behind a club login.

## What it does

- **Finds your results** — paste any race results URL and AI locates your result automatically
- **Claims your history** — review extracted data, make corrections, then claim it permanently
- **Works offline** — full Android app with local Room DB cache; syncs when online
- **Tracks PRs automatically** — personal records recalculated every time you add a result
- **Flexible views** — PRs by distance, race-over-years, pace trends, BQ tracking, age-graded scores
- **Exports** — CSV, PDF race resume, shareable links
- **Secure credentials** — for club/timing sites behind a login (passwords stay on-device only)

## Repo structure

```
trak/                         ← monorepo root
├── CLAUDE.md                 ← Claude Code project instructions
├── .github/workflows/        ← CI/CD (GitHub Actions)
│   ├── backend-ci.yml        ← Backend: test + SAM deploy on merge to main
│   └── android-ci.yml        ← Android: build + test on every PR
├── backend/                  ← AWS Lambda backend (Node.js 20)
│   ├── template.yaml         ← SAM / CloudFormation
│   ├── samconfig.toml        ← Deploy config (dev + prod)
│   ├── functions/            ← extract, claims, results, profile, views, auth
│   ├── shared/               ← db client, raceLogic, response helpers, anthropic
│   └── tests/                ← unit + integration tests
├── android/                  ← Android app (Java, min SDK 26)
│   ├── app/
│   │   └── src/main/java/com/trackmyraces/trak/
│   │       ├── data/         ← Room DB, Retrofit, repositories
│   │       ├── ui/           ← Activities, Fragments, ViewModels
│   │       ├── sync/         ← SyncManager, WorkManager worker
│   │       ├── credential/   ← Android Keystore credential manager
│   │       └── util/         ← TimeFormatter, etc.
│   └── gradle.properties     ← Memory settings — do not change
├── docs/                     ← Architecture docs
│   └── architecture.docx
└── scripts/                  ← Dev utilities
    ├── bootstrap-secrets.sh  ← Create AWS Secrets Manager entries
    ├── gen-token.js          ← Generate a dev JWT for local testing
    └── sync_changed.sh       ← Version diff/sync utility
```

## Tech stack

| Layer     | Technology |
|-----------|-----------|
| Android   | Java, Room, Retrofit, WorkManager, Android Keystore |
| Backend   | AWS Lambda (Node.js 20), API Gateway, DynamoDB |
| AI        | Anthropic claude-sonnet-4 + web_search tool |
| Secrets   | AWS Secrets Manager |
| IaC       | AWS SAM / CloudFormation |
| CI/CD     | GitHub Actions |

## Getting started

### Prerequisites
- Android Studio (for Android development)
- Node.js 20+ and AWS SAM CLI (for backend)
- AWS CLI configured (`aws configure`)
- An Anthropic API key

### Backend setup

```bash
cd backend
npm install

# Create secrets in AWS Secrets Manager (one-time)
../scripts/bootstrap-secrets.sh

# Build and deploy to dev
sam build && sam deploy --config-env dev

# Run backend tests
npm test
```

### Android setup

```bash
# Open android/ in Android Studio
# The project uses Java — do not convert to Kotlin

# Set your dev API URL in android/local.properties (create if absent):
echo 'trak.api.url.dev=https://YOUR_API_ID.execute-api.us-east-1.amazonaws.com/dev' \
  >> android/local.properties

# Build debug APK
cd android && ./gradlew assembleDebug

# Run unit tests
./gradlew test
```

### Emulator fix (macOS — run after system updates)
```bash
sudo xattr -dr com.apple.quarantine ~/Library/Android/sdk/emulator
sudo xattr -dr com.apple.quarantine ~/Library/Android/sdk/platform-tools
```

## Branch strategy

| Branch       | Purpose |
|--------------|---------|
| `main`       | Production-ready code. Protected — PRs only. |
| `develop`    | Integration branch. PRs merge here first. |
| `feature/*`  | New features branched from `develop` |
| `fix/*`      | Bug fixes branched from `develop` |
| `hotfix/*`   | Critical production fixes branched from `main` |

**Flow:** `feature/my-thing` → PR to `develop` → CI runs → merge → PR `develop` to `main` → deploy

## Deployment

Backend deploys automatically via GitHub Actions on merge to `main`.  
Android release builds are triggered manually via the Actions tab.

## Domain

- Web / API: `trackmyraces.com`
- Android package: `com.trackmyraces.trak`
- API base: `https://api.trackmyraces.com/{stage}`
