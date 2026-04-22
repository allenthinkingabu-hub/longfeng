-- refs 落地计划 §5.6 / §5.7 step 4 · 本地开发重置用 · 生产禁用
-- 撤销顺序：与 V1.0.010..V1.0.014 建表相反 (child 先 · 源表后)
DROP TABLE IF EXISTS tag_taxonomy        CASCADE;
DROP TABLE IF EXISTS wrong_item_image    CASCADE;
DROP TABLE IF EXISTS wrong_item_analysis CASCADE;
DROP TABLE IF EXISTS wrong_item_tag      CASCADE;
DROP TABLE IF EXISTS wrong_item          CASCADE;
