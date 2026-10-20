# A — Показать качество и пробелы разговорного корпуса

## Data model / metrics

Use current read-only `PackSnapshot`, not a new duplicate parser. Compute pure offline bounded stats grouped by:
- `PATTERN`: topic/act, source v1/v2/v3/custom, count, duplicate normalized keys, representative source ID/line (only UI); answer availability for corresponding act.
- `TEMPLATE`: act / relationship `Band` / `Register` / mature/profanity; count of clean templates, act with absent coverage, per-bucket occupancy relative to loader's 4096 cap. No false claim that `Topic` of templates exists: templates are keyed by act not topic.
- v3: declared segment inventory/count, per-segment UTF-8 byte size (1,048,576 cap), total v3 bytes (33,554,432), global pattern/template/alias/profanity capacities; manifest maximum 64. Existing shipped manifest has 52 paths (26 semantic + 26 conversation) in public reference; **read ACTUAL LOCAL manifest and report any difference**. Do not raise loader hard bounds.
- Exact duplicates after NFKC/ru/ё; lexical similarity is LIMITED, prefer candidate-scoped/bounded window and deterministic samples, never O(1e6²) or pair every TEMPLATE against 20k. Distinguish near duplicate warning from proven semantic equality; source provenance required.
- Context caveats: BANDS are selection filters, not character ownership; gender filter not implemented for ordinary Java template select; history/social/functional routes not simulated.

**Coverage must not mean language-model accuracy.** `MISSING_TEMPLATE`, `LOW_DIVERSITY`, `NEAR_DUPLICATE_POSSIBLE`, `CAPACITY_WARNING`, `NOT_RUNTIME_PARITY` have explicit definitions, no fictional percentages. No automatic edits to `Candidate.Status`, approvals, lessons, source.

## UI

Separate standard Designer-editable `PackQualityForm` opened from MainForm. Async bounded read/analysis, cancellation; deterministic filters by `topic/act/band/register/source`, sortable summary and explanatory details. Real corpus samples/private strings not automatically displayed/committed; optional aggregate corpus metadata requires explicit choice and stays private. A manual action may prefill existing text composer (user-visible, does not start Gemma or approve) for a selected existing conversational topic/act; never silently generate. Preserve minimum window/scroll.

## Existing tests / new tests

- Fixture with act A having patterns but 0 clean responses, act B with UNKNOWN/FAMILIAR, include mature and profanity: correct clean buckets and warnings.
- v3 52 segments baseline and near 64/1MiB/32MiB boundaries; actual source derived dynamically. Show remaining capacity without forcing segment creation.
- 5k pattern/20k template synthetic fixture bounded time/memory, cancellation, stable deterministic rows under input reordering; exact normalized duplicates case/cyrillic/ё, lexical NOT_SEMANTIC.
- Preview/manual action cannot mutate `snapshot`, `session`, approvals, source, `corpora` or original private ZIP. UI clicks through actual STA controls where available; physical DPI/VS Designer separate.
- Report only aggregate counts/synthetic examples in tracked reports; no raw chat/person-identifying material.

Exit gate A: focused RED→GREEN; full suite remains viable; UI static/STA controls; `A_LOCAL_QUALITY_PASS` != `FULL_SEMANTIC_QUALITY_VERIFIED`.
