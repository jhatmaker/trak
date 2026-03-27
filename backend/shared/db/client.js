'use strict';

/**
 * shared/db/client.js
 * DynamoDB single-table client for Trak.
 *
 * Table key structure:
 *   PK                    SK                  Record
 *   RUNNER#{id}           PROFILE             Runner profile
 *   RUNNER#{id}           CLAIM#{id}          Result claim
 *   RUNNER#{id}           RESULT#{id}         Race result + splits
 *   RUNNER#{id}           CRED#{id}           Credential entry metadata
 *   RUNNER#{id}           VIEW#{id}           Saved view preset
 *   RACE#{id}             META                Canonical race event
 *   RACE#{id}             CLAIM#{id}          Cross-ref: runner claimed this race
 *   EXTRACTION#{id}       DATA                Pending extraction (TTL 24h)
 */

const { DynamoDBClient }        = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient,
        GetCommand, PutCommand, UpdateCommand, DeleteCommand,
        QueryCommand, BatchWriteCommand }  = require('@aws-sdk/lib-dynamodb');

const TABLE = process.env.DYNAMODB_TABLE_NAME;

// Reuse connection across warm invocations
const dynamo = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region: process.env.AWS_REGION || 'us-east-1' }),
  { marshallOptions: { removeUndefinedValues: true } }
);

// ─── Key builders ─────────────────────────────────────────────────────────────

const keys = {
  runner:     (id)              => ({ PK: `RUNNER#${id}`,    SK: 'PROFILE'        }),
  claim:      (runnerId, id)    => ({ PK: `RUNNER#${runnerId}`, SK: `CLAIM#${id}` }),
  result:     (runnerId, id)    => ({ PK: `RUNNER#${runnerId}`, SK: `RESULT#${id}`}),
  cred:       (runnerId, id)    => ({ PK: `RUNNER#${runnerId}`, SK: `CRED#${id}`  }),
  view:       (runnerId, id)    => ({ PK: `RUNNER#${runnerId}`, SK: `VIEW#${id}`  }),
  raceMeta:   (raceId)          => ({ PK: `RACE#${raceId}`,  SK: 'META'           }),
  raceClaim:  (raceId, claimId) => ({ PK: `RACE#${raceId}`,  SK: `CLAIM#${claimId}`}),
  extraction: (id)              => ({ PK: `EXTRACTION#${id}`, SK: 'DATA'          }),
};

// ─── Generic helpers ──────────────────────────────────────────────────────────

async function get(pk, sk) {
  const { Item } = await dynamo.send(new GetCommand({
    TableName: TABLE,
    Key: { PK: pk, SK: sk },
  }));
  return Item || null;
}

async function put(item) {
  const now = new Date().toISOString();
  await dynamo.send(new PutCommand({
    TableName: TABLE,
    Item: { ...item, updatedAt: now, createdAt: item.createdAt || now },
  }));
}

async function update(pk, sk, expression, values, names = {}) {
  await dynamo.send(new UpdateCommand({
    TableName: TABLE,
    Key: { PK: pk, SK: sk },
    UpdateExpression: `${expression}, updatedAt = :_now`,
    ExpressionAttributeValues: { ...values, ':_now': new Date().toISOString() },
    ExpressionAttributeNames: Object.keys(names).length ? names : undefined,
  }));
}

async function softDelete(pk, sk) {
  await update(pk, sk, 'SET #st = :deleted', { ':deleted': 'deleted' }, { '#st': 'status' });
}

async function queryPK(pk, opts = {}) {
  const {
    skPrefix, skBetween, indexName,
    filterExpr, filterValues, filterNames,
    limit, cursor, scanForward = false,
  } = opts;

  const params = {
    TableName: TABLE,
    IndexName: indexName,
    KeyConditionExpression: skPrefix
      ? 'PK = :pk AND begins_with(SK, :prefix)'
      : 'PK = :pk',
    ExpressionAttributeValues: {
      ':pk': pk,
      ...(skPrefix && { ':prefix': skPrefix }),
      ...filterValues,
    },
    ExpressionAttributeNames: filterNames,
    FilterExpression: filterExpr,
    Limit: limit || 100,
    ExclusiveStartKey: cursor ? JSON.parse(Buffer.from(cursor, 'base64').toString()) : undefined,
    ScanIndexForward: scanForward,
  };

  const { Items, LastEvaluatedKey } = await dynamo.send(new QueryCommand(params));
  return {
    items: Items || [],
    cursor: LastEvaluatedKey
      ? Buffer.from(JSON.stringify(LastEvaluatedKey)).toString('base64')
      : null,
  };
}

// ─── Runner profile ───────────────────────────────────────────────────────────

const runner = {
  async get(runnerId) {
    return get(`RUNNER#${runnerId}`, 'PROFILE');
  },

  async put(profile) {
    await put({ ...keys.runner(profile.id), ...profile, entityType: 'PROFILE' });
  },

  async update(runnerId, fields) {
    const setExpr = Object.keys(fields).map((k, i) => `#f${i} = :v${i}`).join(', ');
    const names   = Object.fromEntries(Object.keys(fields).map((k, i) => [`#f${i}`, k]));
    const values  = Object.fromEntries(Object.keys(fields).map((k, i) => [`:v${i}`, fields[k]]));
    await update(`RUNNER#${runnerId}`, 'PROFILE', `SET ${setExpr}`, values, names);
  },

  async softDelete(runnerId) {
    await softDelete(`RUNNER#${runnerId}`, 'PROFILE');
  },
};

// ─── Extractions (pending AI results, TTL 24h) ────────────────────────────────

const extraction = {
  async put(extractionId, data) {
    const ttl = Math.floor(Date.now() / 1000) + 86400; // 24 hours
    await put({
      ...keys.extraction(extractionId),
      entityType: 'EXTRACTION',
      ttl,
      ...data,
    });
  },

  async get(extractionId) {
    return get(`EXTRACTION#${extractionId}`, 'DATA');
  },
};

// ─── Claims ───────────────────────────────────────────────────────────────────

const claims = {
  async put(runnerId, claim) {
    // Write to runner partition and race partition simultaneously
    await Promise.all([
      put({
        ...keys.claim(runnerId, claim.id),
        entityType: 'CLAIM',
        ...claim,
        // GSI1: query all claims for a race (club leaderboard)
        GSI1PK: `RACE#${claim.raceEventId}`,
        GSI1SK: `CLAIM#${claim.id}`,
      }),
      put({
        ...keys.raceClaim(claim.raceEventId, claim.id),
        entityType: 'RACE_CLAIM_XREF',
        runnerId,
        claimId: claim.id,
        raceEventId: claim.raceEventId,
      }),
    ]);
  },

  async get(runnerId, claimId) {
    return get(`RUNNER#${runnerId}`, `CLAIM#${claimId}`);
  },

  async listForRunner(runnerId) {
    const { items } = await queryPK(`RUNNER#${runnerId}`, { skPrefix: 'CLAIM#' });
    return items.filter(i => i.status !== 'deleted');
  },

  async updateStatus(runnerId, claimId, status) {
    await update(`RUNNER#${runnerId}`, `CLAIM#${claimId}`, 'SET #st = :st', { ':st': status }, { '#st': 'status' });
  },

  async softDelete(runnerId, claimId) {
    await softDelete(`RUNNER#${runnerId}`, `CLAIM#${claimId}`);
  },
};

// ─── Race results ─────────────────────────────────────────────────────────────

const results = {
  async put(runnerId, result) {
    await put({
      ...keys.result(runnerId, result.id),
      entityType: 'RESULT',
      ...result,
      // GSI2: query by canonical race name (race-over-years view)
      GSI2PK: `CANONICAL#${result.raceNameCanonical}`,
      GSI2SK: result.raceDate,
    });
  },

  async get(runnerId, resultId) {
    return get(`RUNNER#${runnerId}`, `RESULT#${resultId}`);
  },

  async update(runnerId, resultId, fields) {
    const setExpr = Object.keys(fields).map((k, i) => `#f${i} = :v${i}`).join(', ');
    const names   = Object.fromEntries(Object.keys(fields).map((k, i) => [`#f${i}`, k]));
    const values  = Object.fromEntries(Object.keys(fields).map((k, i) => [`:v${i}`, fields[k]]));
    await update(`RUNNER#${runnerId}`, `RESULT#${resultId}`, `SET ${setExpr}`, values, names);
  },

  async listForRunner(runnerId, opts = {}) {
    const { items } = await queryPK(`RUNNER#${runnerId}`, { skPrefix: 'RESULT#' });
    return items.filter(i => i.status !== 'deleted');
  },

  async listByCanonicalRace(raceNameCanonical) {
    const { Items } = await dynamo.send(new QueryCommand({
      TableName: TABLE,
      IndexName: 'byCanonicalRace',
      KeyConditionExpression: 'GSI2PK = :pk',
      ExpressionAttributeValues: { ':pk': `CANONICAL#${raceNameCanonical}` },
      ScanIndexForward: false,
    }));
    return (Items || []).filter(i => i.status !== 'deleted');
  },

  /** Update isPR flag for all results at a given distance for a runner */
  async recalculatePRs(runnerId, distanceCanonical) {
    const all = await results.listForRunner(runnerId);
    const forDist = all
      .filter(r => r.distanceCanonical === distanceCanonical)
      .sort((a, b) => (a.chipSeconds || a.finishSeconds) - (b.chipSeconds || b.finishSeconds));

    await Promise.all(forDist.map((r, idx) =>
      results.update(runnerId, r.id, { isPR: idx === 0 })
    ));
  },

  async softDelete(runnerId, resultId) {
    await softDelete(`RUNNER#${runnerId}`, `RESULT#${resultId}`);
  },
};

// ─── Race events (canonical, shared across runners) ───────────────────────────

const raceEvents = {
  async put(event) {
    await put({ ...keys.raceMeta(event.id), entityType: 'RACE_EVENT', ...event });
  },

  async get(raceId) {
    return get(`RACE#${raceId}`, 'META');
  },

  async findBySlug(nameSlug, raceDate) {
    // Query GSI2 to find existing canonical race event
    const { Items } = await dynamo.send(new QueryCommand({
      TableName: TABLE,
      IndexName: 'byCanonicalRace',
      KeyConditionExpression: 'GSI2PK = :pk AND GSI2SK BETWEEN :start AND :end',
      ExpressionAttributeValues: {
        ':pk':    `CANONICAL#${nameSlug}`,
        ':start': raceDate.slice(0, 4) + '-01-01',
        ':end':   raceDate.slice(0, 4) + '-12-31',
      },
    }));
    return Items && Items.length ? Items[0] : null;
  },
};

// ─── Credentials (metadata only — password stays on device) ──────────────────

const credentials = {
  async put(runnerId, cred) {
    await put({ ...keys.cred(runnerId, cred.id), entityType: 'CREDENTIAL', ...cred });
  },

  async list(runnerId) {
    const { items } = await queryPK(`RUNNER#${runnerId}`, { skPrefix: 'CRED#' });
    return items.filter(i => i.status !== 'deleted');
  },

  async updateStatus(runnerId, credId, status, lastLoginAt) {
    await update(`RUNNER#${runnerId}`, `CRED#${credId}`,
      'SET #st = :st, lastLoginAt = :ts',
      { ':st': status, ':ts': lastLoginAt },
      { '#st': 'status' }
    );
  },

  async softDelete(runnerId, credId) {
    await softDelete(`RUNNER#${runnerId}`, `CRED#${credId}`);
  },
};

// ─── Saved views ──────────────────────────────────────────────────────────────

const views = {
  async put(runnerId, view) {
    await put({ ...keys.view(runnerId, view.id), entityType: 'VIEW', ...view });
  },

  async list(runnerId) {
    const { items } = await queryPK(`RUNNER#${runnerId}`, { skPrefix: 'VIEW#' });
    return items.filter(i => i.status !== 'deleted');
  },

  async softDelete(runnerId, viewId) {
    await softDelete(`RUNNER#${runnerId}`, `VIEW#${viewId}`);
  },
};

// ─── Per-user site counts ─────────────────────────────────────────────────────
// Stores the last known result count per source for each user.
// Used by /discover to detect new results without an AI call.
//
// DynamoDB key: PK=RUNNER#{userId}, SK=SITE_COUNTS
// Attributes:   { counts: { athlinks: 22, ultrasignup: 5, ... } }

const siteCounts = {
  async get(userId) {
    return get(`RUNNER#${userId}`, 'SITE_COUNTS');
  },

  async put(userId, counts) {
    await put({
      PK: `RUNNER#${userId}`,
      SK: 'SITE_COUNTS',
      entityType: 'SITE_COUNTS',
      counts,
    });
  },
};

module.exports = { runner, extraction, claims, results, raceEvents, credentials, views, siteCounts };
