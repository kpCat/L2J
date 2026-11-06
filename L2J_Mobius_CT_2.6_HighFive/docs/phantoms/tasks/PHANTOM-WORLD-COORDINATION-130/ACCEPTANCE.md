# FULL130 — проверяемый конечный продукт

Все нижеперечисленное относится к текущему final source и одному и тому же final GameServer.jar; старые TASK094/129, mock и кандидатный JAR не подменяют финальный native result. Это **единая вертикаль**, не отдельные микрозадачи и не требование сразу сделать кланы/PK/осады.

## 0. Safety / Source
- Основной `D:\Codex\repos\L2J-phantom-world` (WIP109) read-only; исходная remote WIP129-ветка сохранена. TEST130 и lightweight stage независимы; G0 owner/PID/ports/SQL/Geo203, protected hashes, stop and postflight. Никаких изменения MAIN/PLAY/TEST129/owner secrets/Geo; no force kill; no arbitrary native HP/MP/EXP/SP/item/teleport writes после CUT.
- F7: untagged nonempty mismatched expectedAfterHash fail-closed; replay/corrupt/stale/duplicate receipts regressions. F8: stock Heal regular-player and Phantom HP/MP/skill regression; нет скрытого изменения механики общего Heal.

## A. Logical group with **zero** native players — real persistent repository
- На **трёх** durable, нематериализованных профилях c законными active-level preferences образуется одна логическая группа с постоянными `groupId`, generation, leader, roles, objective. Никакого `Player`/`objectId`/epoch required в этой модели. Быстрый режим 2–9, с гибкими ролями: тест на 2, 3 и 9, конфликт второго соглашения и отказ человека без согласия.
- Единственный group aggregate CAS + idempotent per-profile claims, rollback/compensation для частичной ошибки. DB saved → process+repository recreation → exact roster, goal, background cursor, no native Party. Продолжить background work without overlap native ledger.

## B. Unified liveness & route correctness — fast contract tests
- Intent → Presence → Background → demand → Materialization допускает законный typed `LOGICAL_PARTY_WAKE` и `AMBIENT_TARGET` без ложного current-human-local-only fence; ни одно не наследует acceptedJourney/soft reclaim privileges.
- Отказ содержит `reason`, `owner`, `nextDue` или конкретный `requiredChange`; stale epoch/unsafe owner/WAIT_CAPACITY/WAIT_BACKGROUND не создают бесконечного мгновенного повторения или silent exception. Никаких stale double materializations.
- Показать в deterministic test, что 5-й годный anchor находится после четырёх негодных и 16-й годный товарищ находится после 15 негодных; preview использует разрешённый WALK ingress и различает UNKNOWN от DEFINITIVELY_UNREACHABLE; healer не должен иметь solo DD combat capability для безопасной дороги.
- При завершении операции Combat/Supply/Store группа не отнимает owner; состоявшийся переход не теряет receipts/EXP/SP/items.

## C. Physical census / scale
- Раздельные счётчики `durableManaged`, `scheduledEligible`, `presenceAdmitted`, `materializedManaged`, `nativeOfflineMerchants`, `humanOnline`, `worldTotal`, `physicalTarget`, `physicalCap`, `pending`, `deniedByReason`, `logicalPartyMembers`. В частности, 200/250 offline traders **не** занимают `PhantomMaterializationService` permits и при подсчёте не выдаются за 128 managed materialized.
- `materializedManaged target=128` — отдельная конфигурационная/политическая цель при достаточных легальных доступных profiles/capacity, не только ceiling. Проверить в bounded synthetic/load test на 5000 durable + 250 offline, fair quotas/latencies and no per-pulse full SQL scans. Цифры 128 реально одновременно materialized в живом GameServer не утверждать без census; если целевая плотность не достигнута, выводить точные причины, а не ложный PASS.

## D. Native foreground/background — финальный JAR, новая TEST130
- Согласованная логическая Party сформирована при 0 Player. После CUT один member законно материализуется при наличии физического основания; остальные получают bounded admission, legal movement/GK, не обойдут расписание/slots/Geo. Как минимум **два** managed Player встречаются и через штатные invite/accept формируют native Party. `Player.getParty()` и official leader факт.
- Реальный общий PvE: как минимум **один** mob с damage от Party и real earned EXP/SP или loot; не Solo, не simulated offscreen reward. Сохранить observer trace/source/JAR SHA; идентичность/goal/positions не подменять после CUT.
- После исчезновения внешнего спроса (тестовый наблюдатель или другое допустимое условие) и отсутствия боевых, STORE и REAL Party blocking conditions: graceful native dematerialization/STORE без исчезновения на глазах, **логическая** Party с тем же groupId остаётся. Foreground/Background ledger непересекается; no duplicate reward.

## E. Restart and meaningful continuation — финальный JAR
- Normal STOP/STORE exit0, postflight no own JVM/listeners/SQL sessions, sameDB/sameFINALJAR restart; durable group, profile claims, totals and timers восстановлены.
- После рестарта хотя бы одно новое **полезное** native Party действие (предпочтительно новая earned group kill с damage+reward; если группа физически недоступна, отказ и nextDue объяснимы, но **FULL130 не заявлять**, пока не доказана полезная native activity). Доказать сохранность logical group при 0 World между физическими сессиями.

## F. Compatibility
- Solo фарм, supply/commerce/offline merchant, Party invite/reject/leader, native Heal semantics (особенно ordinary human), PvP/PK legality, official Clan Leader privileges, siege/olympiad hooks не сломаны. Mixed human Party: нельзя автоматически двигать человека или присваивать ему/себе права лидера; когда owner/unique human в Party, native follow может ориентироваться на него безопасно. Проверки ориентированы на регрессию, текущая цель — не самостоятельные клановые войны и осады.

## G. Efficiency and delivery
- Один компактный reusable test toolchain, дешёвые contract tests измерены, без 100+ одноразовых больших скриптов/огромных nested Stage preimages. Source/JAR parity, P1/P2 review, актуальные GameServer stdout/java/error, новая TEST130, protected files; результаты отдельно `MODEL / SQL_INTEGRATION / NATIVE / RESTART`.
- Записать 4 числа: время на production+contract, время на harness/G0/packaging, число реальных GameServer boot, новые полезные действия в финальном JAR. Нельзя говорить FULL из-за 34/34 mocks, чистого STOP или 5000 сохранённых identity.
- Scoped commits/push: только при A–G FULL и verified F7/F8 fast-forward merge в `feature/phantom-world`; в противном случае безопасно запушить текущий связанный прогресс в `wip/coordination-130-preserved` без main update. Если final diff затрагивает native Player/Heal/Teleport, нужны отдельные human regressions.

## Неприемлемый обход
Нельзя вручную записывать Party в SQL, телепортировать Phantom к лидеру после CUT, выдавать предметы и EXP/SP для доказательства, смягчать hash/identity guard, считать background roll настоящим mob kill, менять экономические рейты или редкость PK ради PASS. В тестах допустимы fake ports для contract logic, но они не заменяют native evidence. 18-часовое ожидание одного профиля без полезной causal hypothesis — причина перепроектировать дешёвый слой, а не продолжать.
