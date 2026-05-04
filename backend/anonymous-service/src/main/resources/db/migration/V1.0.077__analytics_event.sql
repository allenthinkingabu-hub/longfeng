-- Analytics event log · plan §S7 BUG-LF-09 fix.
-- Append-only telemetry for landing/guest funnel events. Read out-of-band by BI / DW.
-- C3 RED LINE: lives in the anon schema · never touches wb_* tables.
-- C9: created_at uses TIMESTAMPTZ.
-- guest_session table: already created in V1.0.070 · do NOT recreate here.

CREATE TABLE IF NOT EXISTS anon.analytics_event (
  id          BIGSERIAL    PRIMARY KEY,
  event_name  VARCHAR(64)  NOT NULL,
  device_fp   VARCHAR(128),
  payload     JSONB,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Funnel queries: "show me anon_guest_capture_shoot for the last 24h"
CREATE INDEX IF NOT EXISTS idx_analytics_event_name_time
  ON anon.analytics_event (event_name, created_at DESC);

-- Per-device timelines: "did device X complete the funnel?"
CREATE INDEX IF NOT EXISTS idx_analytics_event_fp_time
  ON anon.analytics_event (device_fp, created_at DESC)
  WHERE device_fp IS NOT NULL;

-- BRIN for cheap range scans by created_at (analytics is naturally append-only)
CREATE INDEX IF NOT EXISTS brin_analytics_event_created
  ON anon.analytics_event USING BRIN (created_at);

COMMENT ON TABLE anon.analytics_event IS
  'Append-only landing/guest funnel telemetry · plan §S7 BUG-LF-09';
COMMENT ON COLUMN anon.analytics_event.payload IS
  'Free-form JSON payload (cta_position / subject / success / quota_remaining / ...)';

-- TODO(post-S7): add a landing_sample table (id, subject, stem_preview, formula, error_reason,
-- kp_label, tag_label, thumbnail_url, ai_analysis_mock JSONB, bucket, sort_order, active)
-- + admin CRUD. For now LandingController returns hard-coded data — see TODO in that class.
