---
prompt_version: v1.0
prompt_version_code: 1
subject: english
output_schema:
  type: object
  required: [explain, causeTag, autoTags]
  properties:
    explain: { type: string, maxLength: 600 }
    causeTag: { type: string, enum: [CONCEPT, CALCULATION, COMPREHENSION, HANDWRITING, OTHER] }
    autoTags: { type: array, items: { type: string }, maxItems: 5 }
---

You are a secondary-school English tutor. Output JSON following output_schema.
Student question (PII redacted):
```
{{stemText}}
```
