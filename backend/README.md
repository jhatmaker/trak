# Trak Backend

AWS Lambda + DynamoDB + Anthropic API backend for the Trak platform.

## Prerequisites

- [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html)
- [Node.js 20+](https://nodejs.org)
- AWS CLI configured (`aws configure`)
- Docker (for local Lambda invocations)

## Setup

```bash
npm install
```

## Secrets (do this before deploying)

Create two secrets in AWS Secrets Manager:

```bash
# Anthropic API key
aws secretsmanager create-secret \
  --name trak/anthropic-api-key \
  --secret-string '{"apiKey":"sk-ant-..."}'

# JWT signing secret (generate a strong random string)
aws secretsmanager create-secret \
  --name trak/jwt-secret \
  --secret-string '{"secret":"your-strong-random-secret-here"}'
```

## Local development

```bash
# Run unit tests
npm test

# Invoke a single function locally (requires Docker)
npm run local:extract

# Invoke with a credentialed-site event
sam local invoke ExtractFunction --event events/extract-credentialed.json

# Start full local API (all endpoints)
sam local start-api --env-vars env.local.json
```

### env.local.json (create locally, do not commit)
```json
{
  "Parameters": {
    "DYNAMODB_TABLE_NAME":   "trak-local",
    "ANTHROPIC_SECRET_NAME": "trak/anthropic-api-key",
    "JWT_SECRET_NAME":       "trak/jwt-secret",
    "STAGE":                 "dev",
    "AWS_REGION":            "us-east-1"
  }
}
```

## Deploying

```bash
# First deploy (guided, sets up S3 bucket etc.)
sam build && sam deploy --guided --config-env dev

# Subsequent dev deploys
sam build && npm run deploy:dev

# Production
sam build && npm run deploy:prod
```

## API Endpoints

| Method | Path                  | Description                    | Auth |
|--------|-----------------------|--------------------------------|------|
| POST   | /extract              | AI extract result from URL     | JWT  |
| POST   | /claims               | Confirm extraction as claim    | JWT  |
| DELETE | /claims/{claimId}     | Remove a claim                 | JWT  |
| GET    | /results              | List results (filterable)      | JWT  |
| GET    | /results/{resultId}   | Single result with splits      | JWT  |
| PUT    | /results/{resultId}   | Correct result fields          | JWT  |
| POST   | /profile              | Create runner profile          | JWT  |
| GET    | /profile              | Get profile                    | JWT  |
| PUT    | /profile              | Update profile                 | JWT  |
| DELETE | /profile              | Soft-delete account            | JWT  |
| GET    | /views                | List saved views               | JWT  |
| PUT    | /views/{viewId}       | Create or update saved view    | JWT  |
| DELETE | /views/{viewId}       | Delete saved view              | JWT  |

## Project structure

```
functions/
  extract/      POST /extract — AI extraction via Anthropic
  claims/       POST/DELETE /claims — claim management
  results/      GET/PUT /results — result queries
  profile/      CRUD /profile — runner profile
  views/        CRUD /views — saved view presets
  auth/         JWT Lambda authoriser

shared/
  db/client.js          DynamoDB single-table client
  utils/raceLogic.js    Distance normalise, age calc, PR logic, BQ check
  utils/response.js     Response helpers, error types, Lambda wrapper
  utils/anthropic.js    Anthropic client, extraction prompt

tests/
  unit/raceLogic.test.js    Pure function unit tests
  integration/              Integration tests (requires local DynamoDB)

events/                     SAM local test event files
template.yaml               SAM/CloudFormation infrastructure
samconfig.toml              Deploy configuration
```

## Key design decisions

- **Single-table DynamoDB** — all entities in one table, composite PK/SK keys
- **Offline-first extractions** — extraction stored with 24h TTL; runner must claim within window
- **Credential security** — session cookies only reach Lambda; passwords never leave Android device
- **Soft deletes** — no hard deletes; `status: 'deleted'` everywhere
- **PR recalculation** — triggered on every claim/delete for that canonical distance
- **Race deduplication** — canonical name slug + year + distance match prevents duplicate RaceEvent records

## Cost estimate (personal use)

| Item              | Monthly cost |
|-------------------|-------------|
| Lambda            | ~$0.00 (free tier) |
| API Gateway       | ~$0.01      |
| DynamoDB          | ~$0.00 (free tier) |
| Secrets Manager   | ~$0.40      |
| Anthropic API     | ~$0.10–0.30 per active user |
| **Total**         | **< $1.00** |
