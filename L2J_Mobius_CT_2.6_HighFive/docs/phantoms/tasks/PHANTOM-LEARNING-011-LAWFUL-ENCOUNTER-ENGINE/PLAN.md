# PLAN011 — исполняемый план и контроль затрат

**Этот файл — исходный план задачи. Codex обновляет после реального preflight и сохраняет исходные ограничения; не переписывает задним числом.**

| Фаза | Что сделать | Условие перехода |
|---|---|---|
| A. Safety | Git feature HEAD/status, существующий TEST006, source004, SQL lineage DRAFT14/Adam903/223, Geo203/protected207 | GO / STOP if unsafe |
| B. Reproduction | Повторить 010 failure categories без GS и показать, где headless/live расходились | факт + negative tests |
| C. Shared contract | Одна schema/validator request+bootstrap+capabilities+world, deterministic bounded search, reason matrix | targeted GREEN |
| D. True admission | Actual Source×NPC×position count and no-solution diagnosis; HEADLESS_READY vs LIVE_ADMITTED разделены | реальные Solo+Party proposals |
| E. Native pilot | Один GS, в нём несколько lawful cells, A/B, без обучения | receipts or exact failure |
| F. Native bounded proof | Если допуск pilot успешен, оставшиеся разрешённые starts, balanced Solo/Party | max3 GS/45min |
| G. Delivery | model cold readback, no credits, STOP/ports/CPU, compile/regressions, feature push/readback | G0–G8 |

Если A/B/C не прошли — GS нельзя запускать. Если D показывает 0 lawful — GS0 и диагностический PARTIAL/BLOCKED. Если первая native попытка доказала систематический дефект parity, сначала исправить его с focused RED/GREEN; не сжигать второй/третий запуск без доказательства.

Уровень новых изменений: минимально существующие java/test/PS файлы. `PhantomCombatService`, native Player, SkillData, Geo, reward/optimizer, MAIN/PLAY и модель V3 не менять. Если модификация этих компонентов необходима — остановиться с ADR выбора, не расширять scope молча.

В task package уже разрешены рутинные исправления и scoped commit/push на `feature/phantom-world`. Если `AGENTS.md` требует approve для новой существенной архитектуры, показать компактный план и запросить только одно решение. Не спрашивать подтверждение каждой обычной проверки.

### Никаких бесконечных починок

Предусмотреть до **двух независимых прямых root cause fixes** по пути общей admission без отдельного `/goal`. После третьего unrelated blocker — Partial/Blocked с отчетом. Это предохранитель от многосуточного Codex, а не повод снижать native законность.

## Исполнение14.11.2026

Исходный план выше сохранён. Exact source allowlist8путей, read-first и принятые технические решения — [IMPLEMENTATION_PLAN](IMPLEMENTATION_PLAN.md). Два причинных направления: shared native admission lifecycle и source-bound preparation/parity. Production owners/mechanics не менялись; расширения architecture scope не потребовалось.

Focused RED→GREEN и JAR пройдены. Fresh Prepare011 дал4preliminary PartyHEAL, SoloDAMAGE0, до paired admission не дошёл. Native0/3 и0/45мин; никакого повторного запуска ради поиска. SQL183 и frozen DRAFT14/Adam903/223/apps48 unchanged, canonical STOPPED/OFF. Итог PARTIAL011, final manual gate REQUIRED; G2PARTIAL/G3FAIL/G4NOT_RUN. Source commitd138611901c, final docs/readback — RESULT/EVIDENCE/COMMANDS.
