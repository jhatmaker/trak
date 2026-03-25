#!/bin/bash
# scripts/bootstrap-secrets.sh
#
# Creates the required AWS Secrets Manager secrets for Trak.
# Run once per AWS account/environment before first deploy.
#
# Usage:
#   ./scripts/bootstrap-secrets.sh dev    # create dev secrets
#   ./scripts/bootstrap-secrets.sh prod   # create prod secrets
#
# Prerequisites: AWS CLI configured with sufficient IAM permissions.

set -euo pipefail

STAGE="${1:-dev}"
REGION="us-east-1"

echo "==> Creating Trak secrets for stage: $STAGE (region: $REGION)"

# ── Anthropic API key ─────────────────────────────────────────────────────────

read -rsp "Enter your Anthropic API key (sk-ant-...): " ANTHROPIC_KEY
echo ""

if [ -z "$ANTHROPIC_KEY" ]; then
  echo "ERROR: Anthropic API key cannot be empty"
  exit 1
fi

aws secretsmanager create-secret \
  --name "trak/anthropic-api-key" \
  --description "Trak — Anthropic API key for AI race result extraction" \
  --secret-string "{\"apiKey\":\"$ANTHROPIC_KEY\"}" \
  --region "$REGION" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --secret-id "trak/anthropic-api-key" \
  --secret-string "{\"apiKey\":\"$ANTHROPIC_KEY\"}" \
  --region "$REGION"

echo "✓ Anthropic API key saved to trak/anthropic-api-key"

# ── JWT signing secret ────────────────────────────────────────────────────────

JWT_SECRET=$(openssl rand -hex 32)

aws secretsmanager create-secret \
  --name "trak/jwt-secret" \
  --description "Trak — JWT signing secret for API authentication" \
  --secret-string "{\"secret\":\"$JWT_SECRET\"}" \
  --region "$REGION" \
  2>/dev/null || \
aws secretsmanager update-secret \
  --secret-id "trak/jwt-secret" \
  --secret-string "{\"secret\":\"$JWT_SECRET\"}" \
  --region "$REGION"

echo "✓ JWT secret generated and saved to trak/jwt-secret"
echo "  JWT secret (save this for Android local.properties): $JWT_SECRET"

echo ""
echo "==> All secrets created. Ready to deploy:"
echo "    cd backend && sam build && sam deploy --config-env $STAGE"
