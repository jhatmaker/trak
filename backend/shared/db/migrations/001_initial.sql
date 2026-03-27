-- 001_initial.sql
-- Initial Aurora PostgreSQL schema for Trak source catalog.
-- Run once against the Aurora cluster after first deploy.
-- Idempotent — safe to re-run (uses IF NOT EXISTS / DO blocks).

-- ── Extensions ────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_uuid()

-- ── race_source — shared catalog of all known result sites ───────────────────
CREATE TABLE IF NOT EXISTS race_source (
  id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  name              text        NOT NULL,
  base_url          text        NOT NULL,
  source_type       text,       -- 'timing_platform','club','parkrun','regional'
  scrape_strategy   text,       -- 'standard_html','credentialed','api','playwright'
  login_required    boolean     NOT NULL DEFAULT false,
  canonical_slug    text        UNIQUE,
  distance_tags     text[]      NOT NULL DEFAULT '{}',
  region_tags       text[]      NOT NULL DEFAULT '{}',
  is_hidden_default boolean     NOT NULL DEFAULT false,
  is_active         boolean     NOT NULL DEFAULT true,
  notes             text,
  last_polled_at    timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);

-- Indexes for common filter queries
CREATE INDEX IF NOT EXISTS idx_race_source_slug      ON race_source(canonical_slug);
CREATE INDEX IF NOT EXISTS idx_race_source_active    ON race_source(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_race_source_type      ON race_source(source_type);

-- ── runner_source_subscription — user ↔ source join table ────────────────────
-- Note: runner_id is a UUID that matches the userId stored on the runner
-- profile (DynamoDB RUNNER#{userId}). There is no runner table in PostgreSQL —
-- the profile lives in DynamoDB. The UUID is the same device-local UUID
-- generated on first profile save (RunnerProfileEntity.userId).
CREATE TABLE IF NOT EXISTS runner_source_subscription (
  id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  runner_id           uuid        NOT NULL,
  source_id           uuid        NOT NULL REFERENCES race_source(id) ON DELETE CASCADE,
  runner_name_on_site text,       -- name to search for on this specific site (overrides profile name)
  bib_override        text,       -- optional bib for sites that need it
  extra_context       text,       -- e.g. "female, age group 40-44"
  is_enabled          boolean     NOT NULL DEFAULT true,
  is_hidden           boolean     NOT NULL DEFAULT false,
  auto_poll           boolean     NOT NULL DEFAULT true,
  poll_frequency      text        NOT NULL DEFAULT 'daily', -- 'hourly','daily','weekly'
  last_polled_at      timestamptz,
  created_at          timestamptz NOT NULL DEFAULT now(),
  UNIQUE(runner_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_sub_runner     ON runner_source_subscription(runner_id);
CREATE INDEX IF NOT EXISTS idx_sub_source     ON runner_source_subscription(source_id);
CREATE INDEX IF NOT EXISTS idx_sub_poll_due   ON runner_source_subscription(last_polled_at)
  WHERE is_enabled = true AND auto_poll = true AND is_hidden = false;
