'use strict';

/**
 * functions/poll-scheduler/index.js
 *
 * Internal Lambda — triggered by EventBridge on a cron schedule (every hour).
 * No HTTP endpoint.
 *
 * Algorithm:
 *   1. Query runner_source_subscription for rows due for polling:
 *        is_enabled = true  AND  auto_poll = true  AND  is_hidden = false
 *        AND (last_polled_at IS NULL
 *             OR last_polled_at < now() - poll_interval)
 *   2. For each due subscription, asynchronously invoke trak-extract-{STAGE}
 *      passing: sourceId, runnerId, runnerNameOnSite, invocation_source='scheduler'
 *   3. Update last_polled_at on each subscription row immediately after invoking
 *      (so a re-run within the same hour doesn't re-queue the same work)
 *   4. Log a summary of what was scheduled.
 *
 * The extract function writes results directly to DynamoDB race_result with
 * status = 'pending' when invocation_source === 'scheduler' (no TTL extraction
 * record is written — results go straight to the runner's inbox).
 */

const { LambdaClient, InvokeCommand } = require('@aws-sdk/client-lambda');
const pg = require('/opt/nodejs/shared/db/postgres');

const lambda         = new LambdaClient({ region: process.env.AWS_REGION || 'us-east-1' });
const EXTRACT_FN     = process.env.EXTRACT_FUNCTION_NAME;

// Poll-frequency → interval expression understood by PostgreSQL
const FREQ_TO_INTERVAL = {
  hourly:  "interval '1 hour'",
  daily:   "interval '24 hours'",
  weekly:  "interval '7 days'",
};

exports.handler = async (event) => {
  console.log(JSON.stringify({ type: 'POLL_SCHEDULER_START', event }));

  // ── 1. Find subscriptions due for polling ─────────────────────────────────
  const rows = await pg.query(`
    SELECT sub.id AS subscription_id,
           sub.runner_id,
           sub.source_id,
           sub.runner_name_on_site,
           sub.bib_override,
           sub.extra_context,
           sub.poll_frequency,
           src.base_url,
           src.canonical_slug,
           src.scrape_strategy,
           src.login_required
    FROM   runner_source_subscription sub
    JOIN   race_source src ON src.id = sub.source_id
    WHERE  sub.is_enabled = true
      AND  sub.auto_poll  = true
      AND  sub.is_hidden  = false
      AND  src.is_active  = true
      AND  (
             sub.last_polled_at IS NULL
             OR sub.last_polled_at < now() - (
               CASE sub.poll_frequency
                 WHEN 'hourly' THEN interval '1 hour'
                 WHEN 'daily'  THEN interval '24 hours'
                 WHEN 'weekly' THEN interval '7 days'
                 ELSE               interval '24 hours'
               END
             )
           )
    ORDER  BY sub.last_polled_at ASC NULLS FIRST
    LIMIT  500
  `);

  console.log(JSON.stringify({ type: 'POLL_SCHEDULER_DUE', count: rows.length }));

  if (rows.length === 0) {
    return { statusCode: 200, body: 'No subscriptions due' };
  }

  // ── 2. Invoke extract function for each due subscription ─────────────────
  let invoked = 0;
  let errors  = 0;

  for (const row of rows) {
    const payload = {
      invocation_source: 'scheduler',
      runnerId:          row.runner_id,
      sourceId:          row.source_id,
      runnerName:        row.runner_name_on_site || null,
      url:               row.base_url,
      extraContext:      row.extra_context       || null,
      bibNumber:         row.bib_override        || null,
    };

    try {
      await lambda.send(new InvokeCommand({
        FunctionName:   EXTRACT_FN,
        InvocationType: 'Event', // async — fire and forget
        Payload:        Buffer.from(JSON.stringify(payload)),
      }));

      // ── 3. Stamp last_polled_at immediately so we don't double-queue ────
      await pg.query(
        `UPDATE runner_source_subscription SET last_polled_at = now() WHERE id = $1`,
        [row.subscription_id]
      );

      invoked++;
    } catch (err) {
      errors++;
      console.log(JSON.stringify({
        type:           'POLL_SCHEDULER_INVOKE_ERROR',
        subscriptionId: row.subscription_id,
        runnerId:       row.runner_id,
        err:            err.message,
      }));
    }
  }

  console.log(JSON.stringify({ type: 'POLL_SCHEDULER_DONE', invoked, errors }));

  return { statusCode: 200, body: `Invoked ${invoked}, errors ${errors}` };
};
