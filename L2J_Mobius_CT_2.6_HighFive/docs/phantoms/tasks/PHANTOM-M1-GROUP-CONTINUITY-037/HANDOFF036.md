# Handoff — точка старта, не перенос GREEN

Repository/base: `0b47eea99c0d7d6190abc1eaa6de70996ca6dba9`.
TASK036 freeze: `1d71344180cc8ff4e12466471efc853ee915dd62`.
Accepted Jar036: `F72E7AD13D3963ADE3C4BEEE9647F30EE97EE5020E0939F7AD6601110993BC24`.

Прочитан опубликованный RESULT_HANDOFF036.md: GREEN_CURRENT_PATH, semanticFix=NONE;
historical27Cause=UNKNOWN. Диагностика cast dispatch исправлена, shared native mechanics
не переписаны. 54 completed cast-launch у 27 в одном epoch; 46 farm cycles после baseline.
FULL_SAVE шести участников и healthy global drain доказаны отчётом. Эти проверки не
переносятся автоматически на новый Jar037 и не означают полноценный group gameplay.

FINAL_SCENE036b:380.63 с,371 frame,maxGap1.09 с, natural cohort:
| profile | object/epoch (object не в public для всех) | Итог |
|---|---|---|
|27|object268484461;epoch457715115727600|continuity PASS, контрольный здоровый caster|
|28|epoch457715135780600|FAIL, exact per-ID faults извлечь из raw|
|40|epoch457715172606200|FAIL, exact per-ID faults извлечь из raw|
|57|epoch457715193072800|FAIL, exact per-ID faults извлечь из raw|
|106|epoch457715210629400|FAIL, exact per-ID faults извлечь из raw|
|173|epoch457715257617300|FAIL, в том числе NATIVE_UNPROVEN; first reason пока не известен|

Для всех 6 FULL_SAVE согласован с SQL; это не доказательство отсутствия промежуточных
разрывов присутствия. Не приписывать всем одинаковый набор ошибок из сводного абзаца.

Приватный архив:
`C:\Users\ZBook\.codex\visualizations\2026\10\11\01a12b3f-7a16-7301-a9ab-116daca1d868\TASK036-review.zip`
SHA256:`99cc1cfba46b98cdca949ce6a07fa1d6d9efe26aba9b5a3af4fde0434252abe8`.
Размер по индексу:58539855 bytes,7819 entries. Проверить hash; извлекать только нужные
raw/headers/helpers с path-traversal защитой. Не создавать ещё одну копию полного runtime.
Внутри нужен `evidence/FINAL_SCENE036b` (найти actual единственный путь, не угадывать prefix):
`baseline-cohort.json`, `all-samples.json`, `primary.json`, `capture-result.json`,
`continuity-v2.json`, `full-native/event-dispatch.tsv`, `stopped-sql`, stock-stop.
Native lifetime ledger указан в EVIDENCE_INDEX036.md:
`evidence/STARTUP032b-2b9bea89fbfe4d6ab7962a71004dc625/lifetimes.jsonl`.
Проверить связи run/epoch/raw SHA, не объединять разные lifetimes по одному profileId.

Источник DB: новый 037 clone из проверенного неизменяемого PREBOOT export, использованного 036b.
Фактический export path/SHA читать из 037 read-first/036 provenance/retained ops036.
Живую 036b или старые 032/034/035 не перезаписывать; post-run data не выдавать за тот же prestate.
Если первичный preboot отсутствует, один новый export из остановленной 036b допустим как
новая baseline с явным отличием; это не exact replay старых 5. Не удалять старые snapshots.

Обязательные защиты 035: max250;36/45 сохраняют 252, hash и object/count/location exact.
Q15 теперь вычисляет expected grant по реальному quest multiplier и проверяет ровно 1 delivery.
Старый expected1→20 больше не считать новым producer bug без фактов.
