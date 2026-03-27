# Trak Backend

AWS Lambda + DynamoDB + Aurora PostgreSQL + Anthropic API backend for the Trak platform.

## Prerequisites

- [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
- [Node.js 20+](https://nodejs.org)
- AWS CLI configured (`aws configure`)
- Docker (for local Lambda invocations)
- An existing AWS VPC with two private subnets in different AZs

## One-time AWS setup

Run these steps once with admin credentials before the first deploy.

### 1. Create application secrets

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

### 2. Store VPC configuration in SSM Parameter Store

CloudFormation reads VPC and subnet IDs from SSM at deploy time, keeping
environment-specific values out of the repo.

```bash
# Find your VPC and private subnet IDs
aws ec2 describe-vpcs --region us-east-1 \
  --query 'Vpcs[*].{Id:VpcId,Name:Tags[?Key==`Name`].Value|[0]}' \
  --output table

aws ec2 describe-subnets --region us-east-1 \
  --query 'Subnets[*].{Id:SubnetId,AZ:AvailabilityZone,Public:MapPublicIpOnLaunch,Name:Tags[?Key==`Name`].Value|[0]}' \
  --output table

# Store values (pick two private subnets — MapPublicIpOnLaunch=False — in different AZs)
aws ssm put-parameter --region us-east-1 \
  --name /trak/dev/vpc-id \
  --value vpc-YOURVALUE \
  --type String

aws ssm put-parameter --region us-east-1 \
  --name /trak/dev/db-subnet-ids \
  --value "subnet-YOURVALUE1,subnet-YOURVALUE2" \
  --type String
```

### 3. Deploy CI/CD IAM roles (OIDC — no stored secrets in GitHub)

```bash
# From the repo root
aws cloudformation deploy \
  --template-file scripts/oidc-roles.yaml \
  --stack-name trak-github-oidc \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

This creates three scoped roles:

| Role | Branch | Permissions |
|---|---|---|
| `trak-github-validate` | any | `cloudformation:ValidateTemplate` only |
| `trak-github-deploy-dev` | `develop` | Lambda, DynamoDB, API Gateway, RDS, EC2, Secrets Manager, SSM — dev resources |
| `trak-github-deploy-prod` | `main` | Lambda, DynamoDB, API Gateway, RDS, EC2, Secrets Manager, SSM — prod resources |

> Re-run this command whenever you update `scripts/oidc-roles.yaml`.

## Local setup

```bash
npm install
```

## Deploying

```bash
# Build (always full — cached is intentionally not set)
sam build

# Deploy to dev (confirm changeset interactively)
sam deploy --config-env dev

# Deploy to dev without confirmation prompt
sam deploy --config-env dev --no-confirm-changeset

# Production
sam build && sam deploy --config-env prod
```

Aurora takes ~5 minutes to provision on first deploy.

## After first deploy — run DB migrations

Once the stack is `UPDATE_COMPLETE`, connect to Aurora and run the migrations:

```bash
# Get the Aurora writer endpoint from CloudFormation outputs
aws cloudformation describe-stacks --stack-name trak-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`AuroraEndpoint`].OutputValue' \
  --output text

# Connect and run migrations
# (requires network access to Aurora — use a bastion host or AWS Cloud9)
psql -h <AURORA_ENDPOINT> -U trakadmin -d trak \
  -f shared/db/migrations/001_initial.sql

psql -h <AURORA_ENDPOINT> -U trakadmin -d trak \
  -f shared/db/seed/sources.sql
```

The seed file uses `ON CONFLICT (canonical_slug) DO NOTHING` — safe to re-run.

## Local development

```bash
# Run unit tests
npm test

# Invoke a single function locally (requires Docker)
sam local invoke ExtractFunction --event events/extract-test.json

# Start full local API (all endpoints)
sam local start-api --env-vars env.local.json
```

### env.local.json (create locally — do not commit)

```json
{
  "Parameters": {
    "DYNAMODB_TABLE_NAME":   "trak-local",
    "ANTHROPIC_SECRET_NAME": "trak/anthropic-api-key",
    "JWT_SECRET_NAME":       "trak/jwt-secret",
    "AURORA_PROXY_ENDPOINT": "localhost",
    "DB_NAME":               "trak",
    "DB_USERNAME":           "trakadmin",
    "STAGE":                 "dev",
    "AWS_REGION":            "us-east-1"
  }
}
```

## API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | /discover | Search default sites for runner | None |
| POST | /extract | AI extract result from URL | JWT |
| POST | /claims | Confirm extraction as claim | JWT |
| DELETE | /claims/{claimId} | Remove a claim | JWT |
| GET | /results | List results (filterable) | JWT |
| GET | /results/{resultId} | Single result with splits | JWT |
| PUT | /results/{resultId} | Correct result fields | JWT |
| POST | /profile | Create runner profile | JWT |
| GET | /profile | Get profile | JWT |
| PUT | /profile | Update profile | JWT |
| DELETE | /profile | Soft-delete account | JWT |
| GET | /views | List saved views | JWT |
| PUT | /views/{viewId} | Create or update saved view | JWT |
| DELETE | /views/{viewId} | Delete saved view | JWT |
| GET | /sources | List source catalog | None |
| GET | /sources/{id} | Single source by UUID | None |
| POST | /sources | Create source (admin only) | JWT |
| PUT | /sources/{id} | Update source (admin only) | JWT |
| GET | /subscriptions | List runner's subscriptions | JWT |
| POST | /subscriptions | Subscribe to a source | JWT |
| PUT | /subscriptions/{id} | Update subscription | JWT |
| DELETE | /subscriptions/{id} | Remove subscription | JWT |

## Project structure

```
functions/
  extract/        POST /extract — AI extraction via Anthropic (JWT)
  discover/       POST /discover — search default sites for runner (public)
  claims/         POST/DELETE /claims — claim management (JWT)
  results/        GET/PUT /results — result queries (JWT)
  profile/        CRUD /profile — runner profile (JWT)
  views/          CRUD /views — saved view presets (JWT)
  auth/           JWT Lambda authoriser
  sources/        GET/POST/PUT /sources — source catalog (GET public, writes admin)
  subscriptions/  GET/POST/PUT/DELETE /subscriptions — runner source subscriptions (JWT)
  poll-scheduler/ EventBridge-triggered poll worker (not deployed — kept for future use)

shared/
  package.json              @anthropic-ai/sdk, pg, @aws-sdk/rds-signer (bundled into layer)
  db/client.js              DynamoDB single-table client
  db/postgres.js            Aurora PostgreSQL client (IAM auth via RDS Proxy)
  db/migrations/
    001_initial.sql         race_source + runner_source_subscription tables
  db/seed/
    sources.sql             Default source catalog (12 sources)
  utils/raceLogic.js        Distance normalise, age calc, PR logic, BQ check
  utils/response.js         Response helpers, error types, Lambda wrapper
  utils/anthropic.js        Anthropic client, extraction + discovery prompts
  config/defaultSites.js    Default running sites for /discover polling

Makefile                    build-SharedLayer target — structures layer for /opt/nodejs/shared/...

tests/
  unit/raceLogic.test.js    Pure function unit tests
  integration/              Integration tests (requires local DynamoDB)

events/                     SAM local test event files
template.yaml               SAM/CloudFormation infrastructure
samconfig.toml              Deploy configuration
```

## SharedLayer build

The layer uses `BuildMethod: makefile` (not `nodejs20.x`). This is intentional:

- SAM's Node.js builder strips the source root, putting files at `nodejs/utils/...`
  instead of `nodejs/shared/utils/...`
- The Makefile copies `shared/` into `ARTIFACTS_DIR/nodejs/shared/`, matching all
  `require('/opt/nodejs/shared/...')` calls in function code
- `npm install` runs inside the layer artifact to bundle `@anthropic-ai/sdk`, `pg`,
  and `@aws-sdk/rds-signer` (none are in the Lambda Node.js 20 runtime)
- `@aws-sdk/*` (DynamoDB, Secrets Manager, SSM) is available from the Lambda
  runtime — not bundled

If you see `Cannot find module '/opt/nodejs/shared/...'` in CloudWatch, the layer
wasn't built/deployed correctly. Run `sam build` (no `--cached`) then `sam deploy`.

## Aurora PostgreSQL connection

Lambda connects to Aurora via RDS Proxy using IAM authentication — no database
passwords in environment variables or Secrets Manager lookups at request time.

```
Lambda (IAM role) → RDS Proxy (IAM auth) → Aurora Serverless v2
```

`shared/db/postgres.js` uses `@aws-sdk/rds-signer` to generate a signed auth token
locally (no external API call) on each new connection. Token expires every 15
minutes; generated fresh per pg pool connection. Pool `max: 1` — one connection
per Lambda container.

The RDS Proxy uses Secrets Manager (`trak/aurora-{stage}`) for its own connection
to Aurora. Lambda never sees that secret.

## Source subscription limits

Enforced in `subscriptionsFunction` POST, not in the poll scheduler:

| Tier | Active sources |
|------|---------------|
| Free | 5 |
| Pro | 25 |
| Club | Unlimited |

## Key design decisions

- **Single-table DynamoDB** — all user entities in one table, composite PK/SK keys
- **Aurora PostgreSQL for shared catalog** — race_source and runner_source_subscription;
  relational joins make sense here, DynamoDB does not
- **IAM auth for Aurora** — no passwords in Lambda env vars; signed tokens generated
  per connection via `@aws-sdk/rds-signer`
- **Offline-first extractions** — extraction stored with 24h TTL; runner must claim within window
- **Credential security** — session cookies only reach Lambda; passwords never leave Android device
- **Soft deletes** — no hard deletes; `status: 'deleted'` everywhere
- **PR recalculation** — triggered on every claim/delete for that canonical distance
- **Race deduplication** — canonical name slug + year + distance match prevents duplicate RaceEvent records
- **Bib number is per-race** — passed as a search hint to Claude during extraction; not stored on the runner profile
- **Elevation at start** — fetched from Open-Meteo geocoding API (free, no key), same call used for weather data
- **Source UUIDs** — stable UUIDs for all sources; never use name or URL as identifier

## Cost estimate (per active Pro user/month, with all mitigations)

| Item | Monthly cost |
|---|---|
| Lambda | ~$0.00 (free tier) |
| API Gateway | ~$0.01 |
| DynamoDB | ~$0.00 (free tier) |
| Aurora Serverless v2 | ~$0.00–$2.00 (0.5 ACU min, scales to 0 when idle) |
| RDS Proxy | ~$0.015/hr (~$11/mo) — fixed cost regardless of users |
| Secrets Manager | ~$0.40 |
| Anthropic API | ~$0.51 light user / ~$1.79 active user |
| **Total** | **~$13–15/mo fixed + ~$0.51–1.79 per active user** |

> Break-even: ~10 Pro users covers all fixed costs including RDS Proxy.
