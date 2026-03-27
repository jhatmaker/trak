'use strict';

/**
 * shared/db/postgres.js
 *
 * Aurora PostgreSQL connection pool via RDS Proxy with IAM authentication.
 *
 * IAM auth flow:
 *   1. @aws-sdk/rds-signer generates a signed auth token locally — no external
 *      API call, uses SigV4 signing with the Lambda execution role credentials.
 *   2. The token is used as the PostgreSQL password when connecting.
 *   3. Tokens expire in 15 minutes; pg calls the `password` function on each
 *      new connection so fresh tokens are always generated automatically.
 *
 * Keep pool max=1 — Lambda handles one request at a time per container.
 * The pool persists across warm invocations (module-level singleton).
 *
 * Required environment variables (set per-function in template.yaml):
 *   AURORA_PROXY_ENDPOINT — RDS Proxy endpoint hostname
 *   DB_NAME               — PostgreSQL database name (e.g. "trak")
 *   DB_USERNAME           — IAM DB user (e.g. "trakadmin")
 *   AWS_REGION            — set automatically by Lambda runtime
 */

const { Signer }   = require('@aws-sdk/rds-signer');
const { Pool }     = require('pg');

const endpoint = process.env.AURORA_PROXY_ENDPOINT;
const dbName   = process.env.DB_NAME;
const username = process.env.DB_USERNAME;
const region   = process.env.AWS_REGION || 'us-east-1';
const port     = 5432;

// Signer instance — reused across invocations (stateless, lightweight)
const signer = new Signer({
  hostname: endpoint,
  port,
  region,
  username,
});

// Pool singleton — persists across warm Lambda invocations
let pool;

function getPool() {
  if (!pool) {
    pool = new Pool({
      host:     endpoint,
      port,
      database: dbName,
      user:     username,
      // password() is called on each new connection — generates a fresh IAM token
      password: () => signer.getAuthToken(),
      ssl:      { rejectUnauthorized: false }, // RDS Proxy uses self-signed cert
      max:      1,          // one connection per Lambda container
      idleTimeoutMillis:   30_000,
      connectionTimeoutMillis: 5_000,
    });

    pool.on('error', (err) => {
      console.log(JSON.stringify({ type: 'PG_POOL_ERROR', err: err.message }));
    });
  }
  return pool;
}

// ─── Query helpers ────────────────────────────────────────────────────────────

/**
 * Run a single parameterised query. Returns all rows.
 * @param {string} text  — SQL with $1, $2, ... placeholders
 * @param {Array}  params
 * @returns {Promise<Array>}
 */
async function query(text, params = []) {
  const client = getPool();
  const { rows } = await client.query(text, params);
  return rows;
}

/**
 * Run a query and return the first row, or null if no rows.
 */
async function queryOne(text, params = []) {
  const rows = await query(text, params);
  return rows[0] || null;
}

/**
 * Run multiple statements in a single transaction.
 * @param {Function} fn  — async (client) => { ... } — use client.query() inside
 */
async function withTransaction(fn) {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { query, queryOne, withTransaction };
