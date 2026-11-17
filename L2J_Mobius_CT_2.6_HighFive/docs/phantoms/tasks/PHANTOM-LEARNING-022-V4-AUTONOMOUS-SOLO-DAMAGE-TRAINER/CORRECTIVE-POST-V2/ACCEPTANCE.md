# G0–G7 — критерии corrective

**Result:** `CORRECTIVE022_POST_V2_READY_FOR_OWNER_REVIEW` только если все G0–G7 прошли. Это НЕ новый FULL боевого AI и НЕ автоматическая ручная приёмка исторического Goal022.

| Gate | Доказательство |
|---|---|
| G0 CURRENT | GitHub HEAD/branch и diff с baseline, чистые task-owned changes; физические V2 authority/claim/HEAD216 доступны, старые файлы byte-identical. |
| G1 V2 IMMUTABLE | Старый V2 остаётся использованным один раз: 3 historical boots и старый claim/authority не менялись, повтор V2/четвёртый boot старой кампании REJECT без новой истории. |
| G2 NEW INTENT | Явный независимый Start разрешает запланировать следующую TEST-кампанию (15/480 по явному выбору), без opt-in старый STOPPED Start REJECT; RUNNING повторный Start не создаёт duplicate writer. |
| G3 LINEAGE | Новый private ledger cold/head наследует точную модель216, globalUpdates216, keys302, sequence2, все immutable seals; cursor4 и следующий step2. Старый V2 lineage/active pointer не изменён после failed admission. |
| G4 OFFLINE PROOF | Prepare-only на isolated TEST fixture действительно проходит production-path bootstrap, создаёт план 480min, не вызывает Broker/GS/SQL, owned0, main owner fixture неизменён, не записывает «8h verified». |
| G5 NEGATIVE | Tampered/missing claim, V2 unauthorized reuse, changed parent/HEAD, missing or forged cursor, stale source, concurrent Start, foreign PID, incomplete reservation → fail closed без data loss / boot. STOP/RESUME сохраняют deadline и не применяют new-campaign semantics. |
| G6 REGRESS | Existing focused Test-Training022 30 checks и новые checks GREEN, статус/отчёт read-only, старые 020/021 route untouched, production OFF, performance/native НЕ запускались. |
| G7 DELIVERY | Scoped diff/check, encoding checks, ordinary feature push, GitHub readback HEAD/parent/full body; отчёт <=140 строк и честный `NOT_RUN` для GS, SQL, 480min, full Ant verify. Manual gate остается REQUIRED. |

**Запрещено** выдавать fake dry-run, переписывать V2 claim, сбрасывать global keys, разрешать 480min без последующей явной команды владельца, повышать предел GS прежней кампании, заявлять superiority. Если частный owner TEST root недоступен, `BLOCKED_CORRECTIVE022_PRIVATE_FIXTURE_UNAVAILABLE`, а не приёмка по симуляции.
