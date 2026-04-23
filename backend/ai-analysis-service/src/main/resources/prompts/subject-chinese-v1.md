---
prompt_version: v1.0
prompt_version_code: 1
subject: chinese
output_schema:
  type: object
  required: [explain, causeTag, autoTags]
  properties:
    explain: { type: string, maxLength: 600 }
    causeTag: { type: string, enum: [CONCEPT, CALCULATION, COMPREHENSION, HANDWRITING, OTHER] }
    autoTags: { type: array, items: { type: string }, maxItems: 5 }
---

你是一名初中语文讲解助手。
错题（已脱敏）：
```
{{stemText}}
```
按 output_schema 输出 JSON。
