'use strict';

/**
 * functions/auth/index.js
 * API Gateway Lambda Authoriser — validates JWT and returns IAM policy.
 *
 * JWT payload expected: { sub: runnerId, exp: timestamp, iat: timestamp }
 * Secret fetched from Secrets Manager (cached for 5 minutes).
 *
 * For Phase 1 / dev: a simple HMAC-SHA256 JWT is sufficient.
 * For prod: swap in AWS Cognito or Auth0 and verify RS256 tokens.
 */

const { SecretsManagerClient, GetSecretValueCommand } = require('@aws-sdk/client-secrets-manager');
const crypto = require('crypto');

const smClient = new SecretsManagerClient({ region: process.env.AWS_REGION || 'us-east-1' });

let _jwtSecret        = null;
let _secretFetchedAt  = 0;
const SECRET_TTL_MS   = 5 * 60 * 1000;

async function getJwtSecret() {
  const now = Date.now();
  if (_jwtSecret && (now - _secretFetchedAt) < SECRET_TTL_MS) return _jwtSecret;

  const { SecretString } = await smClient.send(new GetSecretValueCommand({
    SecretId: process.env.JWT_SECRET_NAME,
  }));
  const parsed = JSON.parse(SecretString);
  _jwtSecret       = parsed.secret;
  _secretFetchedAt = now;
  return _jwtSecret;
}

// ─── Minimal HMAC-SHA256 JWT implementation ───────────────────────────────────

function b64url(str) {
  return Buffer.from(str).toString('base64url');
}

function verifyJWT(token, secret) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Malformed JWT');

  const [headerB64, payloadB64, signatureB64] = parts;
  const expected = crypto
    .createHmac('sha256', secret)
    .update(`${headerB64}.${payloadB64}`)
    .digest('base64url');

  if (expected !== signatureB64) throw new Error('Invalid signature');

  const payload = JSON.parse(Buffer.from(payloadB64, 'base64url').toString());

  if (!payload.sub) throw new Error('Missing sub claim');
  if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) throw new Error('Token expired');

  return payload;
}

// ─── IAM policy builder ───────────────────────────────────────────────────────

function buildPolicy(runnerId, effect, methodArn) {
  // Wildcard ARN allows policy to cover all routes in this API stage
  const arnParts  = methodArn.split(':');
  const region    = arnParts[3];
  const accountId = arnParts[4];
  const apiParts  = arnParts[5].split('/');
  const apiId     = apiParts[0].replace('apigateway', '');
  const stage     = apiParts[1];
  const wildcardArn = `arn:aws:execute-api:${region}:${accountId}:${apiId.replace('apigateway','').replace('execute-api','*')}/${stage}/*/*`;

  return {
    principalId: runnerId,
    policyDocument: {
      Version: '2012-10-17',
      Statement: [{
        Action:   'execute-api:Invoke',
        Effect:   effect,
        Resource: methodArn, // could use wildcardArn to cache policy across routes
      }],
    },
    context: {
      runnerId,          // passed to downstream Lambdas via event.requestContext.authorizer
    },
  };
}

// ─── Handler ──────────────────────────────────────────────────────────────────

exports.handler = async (event) => {
  const token = (event.authorizationToken || '').replace(/^Bearer\s+/i, '');
  const methodArn = event.methodArn;

  if (!token) {
    console.log('AUTH_DENY: no token');
    throw new Error('Unauthorized'); // API Gateway expects thrown error for 401
  }

  try {
    const secret  = await getJwtSecret();
    const payload = verifyJWT(token, secret);

    console.log(JSON.stringify({ type: 'AUTH_ALLOW', runnerId: payload.sub }));
    return buildPolicy(payload.sub, 'Allow', methodArn);

  } catch (err) {
    console.log(JSON.stringify({ type: 'AUTH_DENY', reason: err.message }));
    throw new Error('Unauthorized');
  }
};

// ─── Token generation utility (call from profile creation / login) ────────────
// Not exposed as an API endpoint — call this from a separate auth flow or CLI tool.

function generateToken(runnerId, secret, expiresInSeconds = 86400 * 30) {
  const header  = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({
    sub: runnerId,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
  }));
  const sig = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${sig}`;
}

module.exports = { handler: exports.handler, generateToken };
