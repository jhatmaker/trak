#!/usr/bin/env node
// scripts/gen-token.js
//
// Generates a dev JWT for testing Trak API endpoints locally or with curl.
//
// Usage:
//   node scripts/gen-token.js <runnerId> [expiresInDays]
//
// Example:
//   node scripts/gen-token.js runner-test-123
//   node scripts/gen-token.js runner-test-123 90
//
// The JWT secret is read from AWS Secrets Manager (trak/jwt-secret).
// For quick local testing, set TRAK_JWT_SECRET env var to skip AWS:
//   TRAK_JWT_SECRET=mysecret node scripts/gen-token.js runner-test-123

'use strict';

const crypto = require('crypto');
const { SecretsManagerClient, GetSecretValueCommand } = require('@aws-sdk/client-secrets-manager');

async function getSecret() {
  if (process.env.TRAK_JWT_SECRET) return process.env.TRAK_JWT_SECRET;

  const client = new SecretsManagerClient({ region: 'us-east-1' });
  const { SecretString } = await client.send(new GetSecretValueCommand({ SecretId: 'trak/jwt-secret' }));
  return JSON.parse(SecretString).secret;
}

function b64url(str) {
  return Buffer.from(str).toString('base64url');
}

async function main() {
  const runnerId = process.argv[2];
  const days     = parseInt(process.argv[3] || '30', 10);

  if (!runnerId) {
    console.error('Usage: node scripts/gen-token.js <runnerId> [expiresInDays]');
    process.exit(1);
  }

  const secret  = await getSecret();
  const now     = Math.floor(Date.now() / 1000);
  const exp     = now + days * 86400;

  const header  = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({ sub: runnerId, iat: now, exp }));
  const sig     = crypto.createHmac('sha256', secret)
                        .update(`${header}.${payload}`)
                        .digest('base64url');
  const token   = `${header}.${payload}.${sig}`;

  console.log('\n=== Trak Dev JWT ===');
  console.log(`Runner ID : ${runnerId}`);
  console.log(`Expires   : ${new Date(exp * 1000).toISOString()} (${days} days)`);
  console.log(`\nToken:\n${token}`);
  console.log('\nUse with curl:');
  console.log(`  curl -H "Authorization: Bearer ${token}" https://api.trackmyraces.com/dev/profile`);
  console.log('');
}

main().catch(e => { console.error(e); process.exit(1); });
