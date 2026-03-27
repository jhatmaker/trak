-- seed/sources.sql
-- Seed data for race_source catalog.
-- Run after 001_initial.sql. Uses INSERT ... ON CONFLICT DO NOTHING so it is
-- safe to re-run — existing rows are left unchanged.

INSERT INTO race_source
  (name, base_url, source_type, scrape_strategy, login_required,
   canonical_slug, distance_tags, region_tags, is_hidden_default, notes)
VALUES

-- ── Broad aggregators (always visible to new users) ─────────────────────────
('Athlinks',
 'https://www.athlinks.com',
 'timing_platform', 'api', false,
 'athlinks',
 ARRAY['road','trail','ultra','marathon','triathlon','ocr','track'],
 ARRAY['global'],
 false,
 'Direct REST API — no Claude needed. Largest US race aggregator.'),

('RunSignup',
 'https://runsignup.com',
 'timing_platform', 'standard_html', false,
 'runsignup',
 ARRAY['road','trail','marathon'],
 ARRAY['us'],
 false,
 'Road races across the US. Searched via Claude web_search.'),

('UltraSignup',
 'https://ultrasignup.com',
 'timing_platform', 'standard_html', false,
 'ultrasignup',
 ARRAY['trail','ultra'],
 ARRAY['us'],
 false,
 'Ultra marathon and trail race results.'),

('ChronoTrack',
 'https://results.chronotrack.com',
 'timing_platform', 'standard_html', false,
 'chronotrack',
 ARRAY['road','trail','marathon','triathlon'],
 ARRAY['us'],
 false,
 'Major US timing company covering road, trail, and triathlon.'),

('RaceRoster',
 'https://raceroster.com',
 'timing_platform', 'standard_html', false,
 'raceroster',
 ARRAY['road','trail','marathon','triathlon'],
 ARRAY['us','canada'],
 false,
 'Race registration and results — strong in Canada and US.'),

('Parkrun',
 'https://www.parkrun.com',
 'parkrun', 'api', false,
 'parkrun',
 ARRAY['road','5k'],
 ARRAY['global'],
 false,
 'Free weekly 5K events worldwide. Public API available.'),

('Active.com',
 'https://www.active.com',
 'timing_platform', 'standard_html', false,
 'active',
 ARRAY['road','trail','marathon','triathlon'],
 ARRAY['us'],
 false,
 'Race registration and results portal.'),

('MarathonGuide',
 'https://www.marathonguide.com',
 'timing_platform', 'standard_html', false,
 'marathonguide',
 ARRAY['marathon'],
 ARRAY['us'],
 false,
 'US marathon results going back decades.'),

('New York Road Runners',
 'https://results.nyrr.org',
 'timing_platform', 'standard_html', false,
 'nyrr',
 ARRAY['road','marathon'],
 ARRAY['us-northeast'],
 false,
 'NYRR races including NYC Marathon, Queens 10K, and more.'),

('Boston Athletic Association',
 'https://www.baa.org',
 'timing_platform', 'standard_html', false,
 'baa',
 ARRAY['road','marathon'],
 ARRAY['us-northeast'],
 false,
 'Boston Marathon and BAA road race results.'),

-- ── Niche / credentialed sites (hidden from new users by default) ────────────
('BibRave Results',
 'https://www.bibrave.com',
 'timing_platform', 'standard_html', false,
 'bibrave',
 ARRAY['road','trail','marathon'],
 ARRAY['us'],
 true,
 'Community results pages. Hidden by default — enable if relevant.'),

('RunnerSpace',
 'https://www.runnerspace.com',
 'timing_platform', 'standard_html', false,
 'runnerspace',
 ARRAY['track','crosscountry'],
 ARRAY['us'],
 true,
 'Track and cross country results. Hidden by default.')

ON CONFLICT (canonical_slug) DO NOTHING;
