# Проверки 019 — короткие и причинные

Каждый тест/процесс ≤120 сек offline, одна native проверка ≤8 мин **полностью включая boot/stop**; общие проверки ≤12 мин. Никакой отдельный тест ≥15 мин, никаких широких test/release/security suite. Компиляция/`jar` последовательно. Исторические evidence сохранять.

| ID | Проверка | Честный успех |
|---|---|---|
| T01 | Real016 read-only sample import | ≥1 decoded `TacticalReceiptV3` из native SQL/artifact, exact SHA/actor/epoch/target/episode; без EXP/SP зависимости; counters |
| T02 | Rejections | Fake/unknown source, changed SHA, duplicate eventKey, invalid owner/target/epoch, negative/NaN actual, unexecuted skill rejected |
| T03 | True optimizer | После genuine sample update>0, candidate sha changed, score changes; НЕ просто hand-assigned weights |
| T04 | Cold restore | Byte-for-byte candidate, identical score after reload, corrupt/torn file rejected, immutable old weights untouched |
| T05 | Repeated import/STOP | Reimport same event/run = 0 new credits/updates, checkpoint unchanged; interrupted temp ignored/refused |
| T06 | Rank safety | Fixed test scenario demonstrates bounded change on legal options, identical stock with OFF/unready/corrupt/timeout/critical; no fantasy alternatives |
| T07 | Native one-shot | 1 GS ≤8min, actual lawful solo DAMAGE (or supported HEAL) with V4 scorer consulted and native paid effect, captured scores/model sha/action choice; no 51 cohort |
| T08 | Preservation | TEST OFF/own0/ports free, SQL183 historical exact, TEST2/DRAFT14/Adam903/pointer12 unchanged, Geo/protected and MAIN/PLAY untouched |
| T09 | Code/publish | focused Ant target, jar if source changed, `git diff --check`, scoped non-force push and live GitHub compare/full bodies |

Native test evidence distinguish `MODEL_CONSULTED`, `MODEL_REORDERED`, `NATIVE_EXECUTED`, `EFFECT_PAID`; do not claim the later because former passed. For FULL019 require `MODEL_CONSULTED`, `NATIVE_EXECUTED`, `EFFECT_PAID`, real source training update + cold readback + verified score difference. `MODEL_REORDERED` is desirable but in random conditions may not occur: if not observed, explain whether controlled rank proof verified and note native reordering NOT_PROVEN. **Never force unsafe cast or falsify score margin just to obtain MODEL_REORDERED.**

Если native законно не допускает двух альтернатив в единственном smoke, не раздувать кампанию; честный PARTIAL + exact reason. Не делать повторный live GS без отдельного разрешения.
