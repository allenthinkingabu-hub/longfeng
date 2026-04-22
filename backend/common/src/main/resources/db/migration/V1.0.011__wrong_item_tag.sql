-- refs 业务与技术解决方案 §4.2 wb_question.knowledge_tags (拆出独立表) · 落地计划 §5.6 组 B
CREATE TABLE wrong_item_tag (
  id               BIGINT        PRIMARY KEY,
  wrong_item_id    BIGINT        NOT NULL,
  tag_code         VARCHAR(64)   NOT NULL,
  weight           NUMERIC(4,3)  NOT NULL DEFAULT 1.000,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT fk_wit_item FOREIGN KEY (wrong_item_id) REFERENCES wrong_item(id) ON DELETE RESTRICT,
  CONSTRAINT ck_wit_weight CHECK (weight BETWEEN 0.000 AND 1.000)
);

CREATE UNIQUE INDEX uq_wit_item_tag ON wrong_item_tag(wrong_item_id, tag_code);
CREATE INDEX idx_wit_tag         ON wrong_item_tag(tag_code);
