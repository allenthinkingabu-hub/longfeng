-- refs 业务与技术解决方案 §4.2 wb_question.origin_image / processed_image (拆出独立表 · 多图支持) · 落地计划 §5.6 组 B
CREATE TABLE wrong_item_image (
  id               BIGINT       PRIMARY KEY,
  wrong_item_id    BIGINT       NOT NULL,
  object_key       VARCHAR(512) NOT NULL,
  role             VARCHAR(16)  NOT NULL,
  width_px         INT,
  height_px        INT,
  byte_size        BIGINT,
  content_type     VARCHAR(64),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_wii_item FOREIGN KEY (wrong_item_id) REFERENCES wrong_item(id) ON DELETE RESTRICT,
  CONSTRAINT ck_wii_role CHECK (role IN ('ORIGIN','PROCESSED','CROP','WATERMARK')),
  CONSTRAINT ck_wii_size CHECK (byte_size IS NULL OR byte_size >= 0)
);

CREATE INDEX idx_wii_item            ON wrong_item_image(wrong_item_id);
CREATE UNIQUE INDEX uq_wii_item_origin ON wrong_item_image(wrong_item_id) WHERE role = 'ORIGIN';
