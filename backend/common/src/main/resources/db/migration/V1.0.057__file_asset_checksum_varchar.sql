-- refs 落地计划 §10.1 A6 · S6 JPA 校验对齐
-- V1.0.056 建表用 CHAR(64) · Hibernate strict validation 认为 JPA 默认 @Column(length=64) = VARCHAR · 冲突
-- 改为 VARCHAR(64) · JPA 直接对齐 · 校验通过
ALTER TABLE file_asset
  ALTER COLUMN checksum_sha256 TYPE VARCHAR(64);
