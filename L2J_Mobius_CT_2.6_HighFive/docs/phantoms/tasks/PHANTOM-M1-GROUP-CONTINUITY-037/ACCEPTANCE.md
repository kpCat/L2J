# ACCEPTANCE037 — не отдельный успешный бот

Официальный evaluator: `docs/phantoms/tasks/PHANTOM-M1-LIVING-CONTINUITY-028/proposals/continuity028.py`.
Pinned Git blob:`c441ecc621dc5936ba3b2ea7f8e9f6e6f74eb164`. Полностью read-only.
Исходные пороги и native loss/lineage checks не ослаблять.

| Gate | PASS |
|---|---|
| G0 SOURCE | exact base/remote, isolated existing worktree, checked packet+036 inputs; no foreign mutation |
| G1 CAUSES | все 5 failed IDs классифицированы по исходным фактам; shared root сгруппированы; unknown не выдаётся за proof. Для каждой исправленной ветви current причинное доказательство |
| G2 FIX | genuine RED→GREEN каждого изменения и негативные S05–S09; либо NO_SEMANTIC_CHANGE с доказательством, не «исправил» без diff |
| G3 NATURAL_A |6–8 naturally enrolled IDs,2 primaries,unchanged CONTINUITY_V2 allPASS на current committed artifact |
| G4 NATURAL_B | независимый второй episode с тем же source/Jar/config/catalog/observer/evaluator;6–8ID allPASS; не переигрывание удачных raw |
| G5 CONSERVER | FULL_SAVE каждого enrolled lifetime, sticky invalid отсутствует, no lateSQL substitution, full inventory; exact release после FINALIZED; global typed COMPLETE/retained=0, observer drained, затем OSstop |
| G6 REGRESS | текущие 93/93+11/11,native0369/9,Q15,inventory0356/6 и затронутые маршруты; disclose failures/repeats |
| G7 DELIVERY | exact-path commit+normal push/local=remote; private evidence checked; own JVM0, DB/worktree retained, no deletes |

`GREEN_GROUP_CONTINUITY` — только G0–G7. `PARTIAL_VERIFIED` — частичные доказательства;
`FAILED_GROUP_CONTINUITY` — current full-group FAIL; `BLOCKED_SCOPE/INPUT/CONTRACT` — точная
непреодолённая граница. При любом статусе `M1=OPEN; REAL_FINAL=NOT_RUN`.
Если G1 на historical файлах ограничен законным календарём/смертью и current путь доказан,
можно указать NO_PRODUCT_DEFECT_FOR_THIS_HISTORY с фактами, не требовать бессмысленного fix.
Нельзя объявить GREEN, игнорируя подтверждённый current unresolved semantic failure.

Контракт evaluator narrower than entire M1: lawful death/absence действительно дают FAIL
этого stable-farm сценария, даже при корректном сохранении. Не скрывать это и не замещать
его альтернативной «почти зелёной» метрикой. Если final окно нельзя провести без запрещённой
подмены условий, вернуть BLOCKED_CONTRACT с данными, не исказить поведение сервера.

Не требовать 5cycles от каждого вторичного участника сверх исходного договора:
исходный evaluator требует>=5 у двух primaries и>=2 у остальных, все его прочие counters,
полезный gap<=90 с, tail120 reward, rest45 с сохраняются. Нет downgrade знаменателя 6→4.
Сохранить output legacy-strict с его фактической применимостью; он не заменяет CONTINUITY_V2.
