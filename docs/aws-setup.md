# Trak — AWS Infrastructure Setup Guide

## What the SAM template deploys

Everything in `backend/template.yaml` is ready to go. One `sam deploy` creates:

| Resource | Name | Notes |
|---|---|---|
| DynamoDB table | `trak-dev` / `trak-prod` | Pay-per-request, PITR enabled |
| API Gateway | `trak-api-dev` | HTTPS, CORS, JWT authoriser |
| Lambda: extract | `trak-extract-dev` | 1024 MB, 90s timeout |
| Lambda: claims | `trak-claims-dev` | 512 MB, 60s |
| Lambda: results | `trak-results-dev` | 512 MB, 60s |
| Lambda: profile | `trak-profile-dev` | 512 MB, 60s |
| Lambda: views | `trak-views-dev` | 512 MB, 60s |
| Lambda: auth | `trak-auth-dev` | JWT authoriser |
| Shared layer | `trak-shared-dev` | db client, raceLogic, response, anthropic |
| IAM role | `trak-lambda-role-dev` | Least-privilege: DynamoDB + Secrets Manager only |
| S3 bucket | created by SAM | Stores Lambda deployment packages |

---

## Step 1 — Prerequisites (one-time installs)

### AWS CLI
```bash
# macOS
brew install awscli

# Verify
aws --version   # should show aws-cli/2.x
```

### AWS SAM CLI
```bash
brew install aws-sam-cli
sam --version   # should show SAM CLI, version 1.x
```

### Node.js 20
```bash
brew install node@20
node --version  # should show v20.x
```

---

## Step 2 — Create an IAM user for deployments

**Do not deploy using your root AWS account.** Create a dedicated IAM user.

1. Go to **AWS Console → IAM → Users → Create user**
2. Name: `trak-deploy`
3. Attach these managed policies directly:
   - `AWSCloudFormationFullAccess`
   - `AWSLambda_FullAccess`
   - `AmazonAPIGatewayAdministrator`
   - `AmazonDynamoDBFullAccess`
   - `IAMFullAccess`
   - `AmazonS3FullAccess`
   - `SecretsManagerReadWrite`
4. Create access keys (Access key → CLI use case)
5. Save the **Access Key ID** and **Secret Access Key** — you only see the secret once

### Configure the AWS CLI
```bash
aws configure
# AWS Access Key ID:     paste your key ID
# AWS Secret Access Key: paste your secret key
# Default region:        us-east-1
# Default output format: json
```

Verify it works:
```bash
aws sts get-caller-identity
# Should show your account ID and the trak-deploy user ARN
```

---

## Step 3 — Create the S3 artifact bucket

SAM needs an S3 bucket to store Lambda deployment packages.

```bash
# Dev bucket
aws s3 mb s3://trak-sam-artifacts-dev --region us-east-1

# Prod bucket (create now, use later)
aws s3 mb s3://trak-sam-artifacts-prod --region us-east-1
```

---

## Step 4 — Create secrets in AWS Secrets Manager

Run the bootstrap script (it prompts for your Anthropic key and auto-generates the JWT secret):

```bash
cd /path/to/trak
chmod +x scripts/bootstrap-secrets.sh
./scripts/bootstrap-secrets.sh dev
```

This creates two secrets:
- `trak/anthropic-api-key` — your Anthropic API key
- `trak/jwt-secret` — a random 64-char hex string used to sign JWTs

**Save the JWT secret output** — you'll need it for the Android `local.properties`.

To verify the secrets were created:
```bash
aws secretsmanager list-secrets --query "SecretList[?contains(Name,'trak')].Name"
# Should show: ["trak/anthropic-api-key", "trak/jwt-secret"]
```

---

## Step 5 — Deploy the backend

```bash
cd trak/backend

# Install dependencies
npm install

# Build (packages Lambda functions and shared layer)
sam build

# First deploy — guided, sets up samconfig.toml
sam deploy --guided --config-env dev
```

The guided deploy will ask:
- Stack name: `trak-dev` ✓ (already in samconfig.toml)
- AWS Region: `us-east-1` ✓
- Parameter Stage: `dev` ✓
- Confirm changeset: `Y`
- Allow SAM to create IAM roles: `Y`
- Save arguments to samconfig: `Y`

After the first deploy, subsequent ones are just:
```bash
sam build && sam deploy --config-env dev
```

---

## Step 6 — Note your API URL

After deploy completes, the API URL is printed in the Outputs section:

```
Outputs
-------
Key   ApiUrl
Value https://XXXXXXXXXX.execute-api.us-east-1.amazonaws.com/dev
```

**Copy this URL** — you need it in the Android `local.properties`.

You can also retrieve it anytime:
```bash
aws cloudformation describe-stacks \
  --stack-name trak-dev \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" \
  --output text
```

---

## Step 7 — Test the deployment

### Generate a dev JWT
```bash
cd trak
TRAK_JWT_SECRET=your-jwt-secret-from-step-4 \
  node scripts/gen-token.js runner-test-001
# Prints a Bearer token
```

### Test the profile endpoint
```bash
curl -X POST https://YOUR_API_URL/profile \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Smith",
    "dateOfBirth": "1983-03-15",
    "gender": "F",
    "preferredUnits": "metric"
  }'
# Should return: {"id":"runner-test-001","name":"Jane Smith",...}
```

### Test extraction
```bash
curl -X POST https://YOUR_API_URL/extract \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://runsignup.com/Race/Results/12345",
    "runnerName": "Jane Smith"
  }'
# Should return extraction result (takes 10-20s)
```

---

## Step 8 — Configure Android to point at dev API

Create `trak/android/local.properties` (already in .gitignore):

```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
trak.api.url.dev=https://YOUR_API_URL_FROM_STEP_6
```

The debug build automatically uses `trak.api.url.dev`.

---

## Step 9 — Set up GitHub Actions CI/CD (optional but recommended)

In your GitHub repo → **Settings → Secrets and variables → Actions**, add:

| Secret name | Value |
|---|---|
| `AWS_ACCESS_KEY_ID` | Dev IAM user access key ID |
| `AWS_SECRET_ACCESS_KEY` | Dev IAM user secret key |
| `AWS_ACCESS_KEY_ID_PROD` | Prod IAM user access key ID (same for now) |
| `AWS_SECRET_ACCESS_KEY_PROD` | Prod IAM user secret key |
| `KEYSTORE_BASE64` | Base64-encoded Android release keystore (later) |
| `KEYSTORE_PASSWORD` | Android keystore password (later) |
| `KEY_ALIAS` | Android key alias (later) |
| `KEY_PASSWORD` | Android key password (later) |

After this, every push to `develop` auto-deploys backend to dev, and every push to `main` deploys to prod.

---

## Monitoring and costs

### View Lambda logs
```bash
# Live tail
sam logs -n ExtractFunction --stack-name trak-dev --tail

# Last 5 minutes
aws logs tail /aws/lambda/trak-extract-dev --since 5m
```

### Check DynamoDB
```bash
# Item count
aws dynamodb describe-table --table-name trak-dev \
  --query "Table.ItemCount"

# Scan a few items (dev only)
aws dynamodb scan --table-name trak-dev --max-items 5
```

### Monthly cost estimate (personal use)
| Service | Cost |
|---|---|
| Lambda | ~$0.00 (free tier: 1M requests/month) |
| API Gateway | ~$0.01 |
| DynamoDB | ~$0.00 (free tier: 25 WCU/RCU) |
| Secrets Manager | ~$0.80 (2 secrets × $0.40) |
| S3 (artifacts) | ~$0.01 |
| Anthropic API | ~$0.10–0.30 per active user/month |
| **Total** | **~$1/month** |

---

## Tearing down (if needed)

```bash
# Delete the dev stack (keeps S3 bucket and secrets)
aws cloudformation delete-stack --stack-name trak-dev

# Delete secrets
aws secretsmanager delete-secret --secret-id trak/anthropic-api-key --force-delete-without-recovery
aws secretsmanager delete-secret --secret-id trak/jwt-secret --force-delete-without-recovery

# Empty and delete S3 bucket
aws s3 rm s3://trak-sam-artifacts-dev --recursive
aws s3 rb s3://trak-sam-artifacts-dev
```

Note: The DynamoDB table has `DeletionPolicy: Retain` — it survives stack deletion. Delete it manually if needed:
```bash
aws dynamodb delete-table --table-name trak-dev
```
