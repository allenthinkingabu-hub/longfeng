-- refs 业务与技术解决方案 §4.10 image_tmp_url + analysis_result_json (拆出独立表) · 落地计划 §5.6 组 C
-- 匿名错题暂存 · claim 后 promoted 到主 wrong_item
CREATE TABLE guest_wrong_item (
  id                      BIGINT       PRIMARY KEY,
  guest_session_id        BIGINT       NOT NULL,
  origin_image_key        VARCHAR(512) NOT NULL,
  ocr_text                TEXT,
  stem_text               TEXT,
  analysis_raw_json       JSONB,
  status                  SMALLINT     NOT NULL DEFAULT 0,
  promoted_wrong_item_id  BIGINT,
  created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at              TIMESTAMPTZ  NOT NULL,
  CONSTRAINT fk_gwi_session   FOREIGN KEY (guest_session_id)       REFERENCES guest_session(id) ON DELETE RESTRICT,
  CONSTRAINT fk_gwi_promoted  FOREIGN KEY (promoted_wrong_item_id) REFERENCES wrong_item(id)    ON DELETE RESTRICT,
  CONSTRAINT ck_gwi_status    CHECK (status IN (0,1,2,9))
);

CREATE INDEX idx_gwi_session ON guest_wrong_item(guest_session_id);
CREATE INDEX idx_gwi_raw_gin ON guest_wrong_item USING GIN (analysis_raw_json);
