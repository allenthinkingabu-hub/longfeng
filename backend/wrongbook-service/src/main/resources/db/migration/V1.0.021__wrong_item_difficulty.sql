-- S3 · D2 drift resolution (Q2-R1) · ADR 0012 difficulty column
-- NULL = unset; 1..5 = teacher/student graded difficulty.
ALTER TABLE wrong_item ADD COLUMN IF NOT EXISTS difficulty SMALLINT
  CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5);
