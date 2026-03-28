'use strict';

/**
 * shared/utils/auth.js
 * JWT generation shared by register, login, and the JWT authoriser.
 */

const { SecretsManagerClient, GetSecretValueCommand } = require('@aws-sdk/client-secrets-manager');
const crypto = require('crypto');

const smClient = new SecretsManagerClient({ region: process.env.AWS_REGION || 'us-east-1' });

let _jwtSecret       = null;
let _secretFetchedAt = 0;
const SECRET_TTL_MS  = 5 * 60 * 1000;

async function getJwtSecret() {
  const now = Date.now();
  if (_jwtSecret && (now - _secretFetchedAt) < SECRET_TTL_MS) return _jwtSecret;

  const { SecretString } = await smClient.send(new GetSecretValueCommand({
    SecretId: process.env.JWT_SECRET_NAME,
  }));
  _jwtSecret       = JSON.parse(SecretString).secret;
  _secretFetchedAt = now;
  return _jwtSecret;
}

function b64url(str) {
  return Buffer.from(str).toString('base64url');
}

/**
 * Generate a signed HS256 JWT.
 * @param {string} runnerId  — becomes the `sub` claim
 * @param {number} expiresInSeconds  — default 30 days
 */
async function generateToken(runnerId, expiresInSeconds = 86400 * 30) {
  const secret  = await getJwtSecret();
  const header  = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({
    sub: runnerId,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
  }));
  const sig = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${sig}`;
}

module.exports = { getJwtSecret, generateToken };
