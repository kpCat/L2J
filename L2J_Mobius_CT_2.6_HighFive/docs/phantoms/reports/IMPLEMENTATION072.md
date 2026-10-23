# PHANTOM-SUSTAINABLE-LIFE-072 Implementation Plan

**Goal:** один честный native earned → paid D-shots → return → rewarded combat → sameDB restart цикл; отдельно CP purchase и PvP shortage.

**Architecture:** Java25/Ant и существующие CombatService/PvpService/SupplyJourney. Сцена выбирается до создания нового актёра. Production changes допустимы только после причинного native failure, без нового AI.

**Spec:** ../tasks/PHANTOM-SUSTAINABLE-LIFE-072/TASK.md и весь неизменяемый пакет072.

## Ограничения и scope

Один исполнитель, текущий checkout feature/phantom-world. Start 2026-10-23 17:28:47 UTC; product freeze 21:28:47 UTC, hard stop 22:28:47 UTC. Цель 1,5–3 часа, досрочное завершение при результате. Старые root/DB/акторы, 068V dirt и клиент защищены. No worktrees, force, reset, synthetic rewards, post-measurement relocation/refill.

Bounded exception >10 files: новые072 wrappers/lane/environment/native tests плюс отчёт/TSV и неизменённый task package. Не более пяти owner families: runtime ownership/build; native fixture/preflight; combat/PvP; supply/commerce; evidence/docs. Только доказанный дефект разрешает изменение существующего production owner.

Прочитаны root/module AGENTS, root README, workflow/release/current status, master plan/task standard (релевантные разделы), весь пакет072, отчёт/матрица/post-audit071, Environment/Lane/HeadlessPlayerTestEnvironment, JourneyNative/CombatNative071, Build/Assemble/Runtime/Common071, native shot control и supply policy. Module README отсутствует. Аналоги: one-shot Environment071, physical Geo/candidate067 input, Journey071 с native receipts и action leases. Реальные scene/CP layer/earned budget пока NOT_RUN.

## Шаги и проверяемые результаты

- [ ] 1. Создать новые OWN072 wrappers и строгие lane/environment по071. Проверить absence обеих схем/roots и свободные порты. Не читать private071. Раздельные UUID/hexid и schema task_id72, исходная migration071 SHA неизменна.
- [ ] 2. Новый native scene runner: bounded actual20+ mobs/D merchant, Geo walkSurface, оба коридора, reward level gate, source quote и CP final layer. SCENE072 до account/Player creation. Если нет входов — BLOCKED_INPUT с тремя причинами.
- [ ] 3. Один finite lvl20+ fixture, нулевые Adena/shots: 2–5 native reward kills → earned funds → существующий SupplyJourney paid1463/return → две новые rewarded kills со shot debit. Сохранить snapshots и тот же actor после restart; никакого reseed.
- [ ] 4. CP: fresh finite2000A fixture на проверенной поверхности; COMMITTED5592/physical deltas и native handler usage/reuse/Olympiad guard. PvP: existing owners, actual cause/skill/debit → SUPPLY_REQUIRED → retreat/replan → одна lawful supply attempt. Gear partial если нет earned funds; optional epic≤10min либо reasoned SKIP.
- [ ] 5. Exact source commit → один archive Ant jar compile-tests; focused suites и native evidence из этой сборки. Отдельный RELEASE072,203physicalGeo, ordinary Main≤8min и sameDB/sameJAR restart; штатный STOP, DB/JVM/listeners0.
- [ ] 6. Fresh read-only review, честная acceptance matrix, отдельные mojibake и escaped Cyrillic scans, exact diff --check, exact stage/normal commit/non-force push и remote compare. Отчёт не требует повторной сборки неизменившегося product source.

## Review focus

Reward eligibility до Player; layer после load; earned vs fixture provenance; immutable persisted actor/receipt across restart; wrong owner/port/DB fails closed. Native results определяют PASS/PARTIAL, исторический FULL071=PARTIAL сохраняется.

Ruling: пользователь и AUTHORITY072 уже выбрали автономное выполнение; дополнительных design/plan approval gates нет. Native implementation ведёт основной исполнитель, final reviewer только read-only.
