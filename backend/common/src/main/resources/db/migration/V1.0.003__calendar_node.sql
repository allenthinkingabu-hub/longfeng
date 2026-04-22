-- refs 业务与技术解决方案 §4.9 calendar_event 联动 · 落地计划 §5.6 组 A · 通用日历节点
-- A6: FK RESTRICT · 业务主表
CREATE TABLE calendar_node (
  id               BIGINT       PRIMARY KEY,
  owner_user_id    BIGINT       NOT NULL,
  kind             VARCHAR(16)  NOT NULL,
  title            VARCHAR(256) NOT NULL,
  relation_type    VARCHAR(16),
  relation_id      BIGINT,
  starts_at        TIMESTAMPTZ  NOT NULL,
  ends_at          TIMESTAMPTZ,
  status           SMALLINT     NOT NULL DEFAULT 0,
  payload          JSONB,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ,
  CONSTRAINT fk_cal_owner     FOREIGN KEY (owner_user_id) REFERENCES user_account(id) ON DELETE RESTRICT,
  CONSTRAINT ck_cal_kind      CHECK (kind IN ('STUDY','TASK','REMINDER','NOTE')),
  CONSTRAINT ck_cal_status    CHECK (status IN (0,1,2,9)),
  CONSTRAINT ck_cal_ends_gte  CHECK (ends_at IS NULL OR ends_at >= starts_at)
);

CREATE INDEX idx_cal_owner_starts ON calendar_node(owner_user_id, starts_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_cal_relation     ON calendar_node(relation_type, relation_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cal_payload_gin  ON calendar_node USING GIN (payload);
