# SOURCES_REDESIGN.md
# Architecture change — shared source catalog + user subscriptions
# Read this before touching anything in backend/functions/ or the database layer.

## What changed and why

The original design stored source URLs directly on individual user records
in DynamoDB. This is being replaced with a proper shared source catalog in
Aurora PostgreSQL Serverless v2. Every known race results website lives in
the catalog exactly once, identified by a stable UUID (GUID). Users subscribe
to sources from the catalog rather than owning copies.

This matters for three reasons:
1. When a source changes its HTML structure, we fix the scraping strategy once
   and it benefits every subscriber automatically.
2. We can poll sources on a shared schedule rather than duplicating work across
   users who all want the same site.
3. It enables the tiered visibility model described below.

## New AWS resource — Aurora PostgreSQL Serverless v2

Add to template.yaml alongside the existing DynamoDB table. Lambda functions
that need relational queries connect via RDS Proxy (handles connection pooling
for serverless environments). Keep DynamoDB for hot per-user data (results
list, profile, saved views). Use Aurora for the shared relational data below.

## Database schema — new tables in PostgreSQL

### race_source  (the shared catalog)
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid()
  name              text NOT NULL           -- "RunSignup", "Athlinks", "Quincy Running Club"
  base_url          text NOT NULL           -- "https://runsignup.com"
  source_type       text                    -- 'timing_platform', 'club', 'parkrun', 'regional'
  scrape_strategy   text                    -- 'standard_html', 'credentialed', 'api', 'playwright'
  login_required    boolean DEFAULT false
  canonical_slug    text UNIQUE             -- 'runsignup', 'athlinks', 'parkrun-quincy'
  distance_tags     text[]                  -- ['5k','10k','halfmarathon'] — for interest matching
  region_tags       text[]                  -- ['us-northeast','florida','global']
  is_hidden_default boolean DEFAULT false   -- hidden from new users until explicitly enabled
  is_active         boolean DEFAULT true
  notes             text
  last_polled_at    timestamptz
  created_at        timestamptz DEFAULT now()

### runner_source_subscription  (user ↔ source join table)
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid()
  runner_id           uuid NOT NULL REFERENCES runner(id)
  source_id           uuid NOT NULL REFERENCES race_source(id)
  runner_name_on_site text            -- name to search for on this specific site
  bib_override        text            -- optional bib for sites that need it
  extra_context       text            -- e.g. "female, age group 40-44"
  is_enabled          boolean DEFAULT true
  is_hidden           boolean DEFAULT false   -- user hid this source
  auto_poll           boolean DEFAULT true
  poll_frequency      text DEFAULT 'daily'    -- 'hourly','daily','weekly'
  last_polled_at      timestamptz
  created_at          timestamptz DEFAULT now()
  UNIQUE(runner_id, source_id)

## Source visibility model

Every new runner gets a row in runner_source_subscription for every source
in race_source where is_hidden_default = false. This is their initial list.
Sources where is_hidden_default = true exist in the catalog but do NOT get
a subscription row created automatically — the runner has to explicitly
go into "hidden sources" and enable them.

When a runner completes onboarding and selects their race interests (e.g.
road races, New England region, marathon distance), the system creates
subscriptions only for sources whose distance_tags and region_tags overlap
with those interests. Sources outside their interests start hidden.

The runner can then:
  - Enable any hidden source (adds subscription row, is_hidden = false)
  - Hide any visible source (sets is_hidden = true on existing row)
  - Delete a subscription entirely (removes row, source goes back to hidden)

## Source limits (enforcement in subscriptions Lambda)

Free tier:  max 5 active enabled sources (is_enabled = true, is_hidden = false)
Pro tier:   max 25 active enabled sources
Club tier:  unlimited

Rationale: each polling cycle calls Anthropic for every enabled source per
user. Unlimited sources on free tier would make the cost model unworkable.
The limit is on active/enabled sources — a user can have 100 sources in
their list as long as no more than 5/25 are actively polled.

## New Lambda functions to build

### sourcesFunction  (GET/POST/PUT /sources)
  GET  /sources           — list all sources in catalog (supports filter by
                            type, region, distance, hidden status)
  GET  /sources/{id}      — get one source by UUID
  POST /sources           — admin only — add a new source to the catalog
  PUT  /sources/{id}      — admin only — update scraping strategy, tags etc.

### subscriptionsFunction  (GET/POST/PUT/DELETE /subscriptions)
  GET    /subscriptions              — list current runner's subscriptions
  POST   /subscriptions              — add a source to runner's list
                                       body: { sourceId, runnerNameOnSite,
                                               bibOverride, extraContext }
  PUT    /subscriptions/{id}         — update (toggle enabled, hidden, name)
  DELETE /subscriptions/{id}         — remove subscription entirely

### pollSchedulerFunction  (internal — triggered by EventBridge)
  No HTTP endpoint. Triggered on a cron schedule (e.g. every hour).
  Algorithm:
    1. Query runner_source_subscription WHERE auto_poll = true
       AND is_enabled = true
       AND (last_polled_at IS NULL
            OR last_polled_at < now() - interval based on poll_frequency)
    2. For each due subscription, invoke extractFunction asynchronously
       (Lambda invoke, not HTTP) passing sourceId, runnerId, runnerNameOnSite
    3. Update last_polled_at on the subscription row
    4. Any extracted results with status = 'pending' land in race_result
       and trigger a push notification to the runner's device

## What stays unchanged

  - DynamoDB table (trak-dev / trak-prod) — keep for all existing entities:
    runner profile, race_result, result_split, saved_view, credential_entry
  - All existing Lambda functions (extract, claims, results, profile, views, auth)
    continue working exactly as before
  - extractFunction is now called both by user-initiated requests (HTTP) AND
    by pollSchedulerFunction (internal Lambda invoke). It needs to handle both
    call shapes — add an invocation_source field ('user' or 'scheduler') to
    distinguish and skip the 24h TTL extraction record when called by scheduler
    (write the result directly to race_result with status = 'pending' instead)

## Source GUIDs

Every source in race_source has a stable UUID generated by PostgreSQL on
insert (gen_random_uuid()). This UUID is what gets stored in
runner_source_subscription.source_id. Never use the source name or URL as
an identifier — those can change. The UUID is permanent.

Seed data: populate race_source on first deploy with the known platforms:
  RunSignup, Athlinks, UltraSignup, ChronoTrack, RaceRoster, RunnerSpace,
  Parkrun (global), Active.com, MarathonGuide, BibRave results pages.
Each gets a canonical_slug, distance_tags, region_tags, and
is_hidden_default = false. Club/regional sources added later get
is_hidden_default = true until the admin promotes them.

## Files to create or modify

New files:
  backend/functions/sources/index.js
  backend/functions/subscriptions/index.js
  backend/functions/poll-scheduler/index.js
  backend/shared/db/postgres.js          — Aurora connection via pg + RDS Proxy
  backend/shared/db/migrations/001_initial.sql
  backend/shared/db/seed/sources.sql

Modify:
  backend/template.yaml                  — add Aurora cluster, RDS Proxy,
                                           new Lambda functions, EventBridge rule
  backend/functions/extract/index.js     — handle scheduler invocation shape
  backend/CLAUDE.md (root CLAUDE.md)     — add Aurora connection details,
                                           new function descriptions

Do NOT modify:
  Any existing DynamoDB access patterns in shared/db/client.js
  Any existing Lambda function logic other than extract/index.js