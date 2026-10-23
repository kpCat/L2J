# PHANTOM-SUSTAINABLE-LIFE-072 Implementation Plan

**Goal:** один честный native earned → paid D-shots → return → rewarded combat → sameDB restart цикл; отдельно CP purchase и PvP shortage.

**Architecture:** Java25/Ant и существующие CombatService/PvpService/SupplyJourney. Сцена выбирается до создания нового актёра. Production changes допустимы только после причинного native failure, без нового AI.

**Spec:** ../tasks/PHANTOM-SUSTAINABLE-LIFE-072/TASK.md и весь неизменяемый пакет072.

## Ограничения и scope

Один исполнитель, текущий checkout feature/phantom-world. Start 2026-10-23 17:28:47 UTC; product freeze 21:28:47 UTC, hard stop 22:28:47 UTC. Цель 1,5–3 часа, досрочное завершение при результате. Старые root/DB/акторы, 068V dirt и клиент защищены. No worktrees, force, reset, synthetic rewards, post-measurement relocation/refill.

Bounded exception >10 files: новые072 wrappers/lane/environment/native tests плюс отчёт/TSV и неизменённый task package. Не более пяти owner families: runtime ownership/build; native fixture/preflight; combat/PvP; supply/commerce; evidence/docs. Только доказанный дефект разрешает изменение существующего production owner.

Прочитаны root/module AGENTS, root README, workflow/release/current status, master plan/task standard (релевантные разделы), весь пакет072, отчёт/матрица/post-audit071, Environment/Lane/HeadlessPlayerTestEnvironment, JourneyNative/CombatNative071, Build/Assemble/Runtime/Common071, native shot control и supply policy. Module README отсутствует. Аналоги: one-shot Environment071, physical Geo/candidate067 input, Journey071 с native receipts и action leases. Реальные scene/CP layer/earned budget пока NOT_RUN.

## Шаги и проверяемые результаты

- [x] 1. Создать новые OWN072 wrappers и строгие lane/environment по071. Проверить absence обеих схем/roots и свободные порты. Не читать private071. Собственный task installationUUID, отдельные TEST/RELEASE hexid и schema task_id72, исходная migration071 SHA неизменна.
- [ ] 2. Новый native scene runner: bounded actual20+ mobs/D merchant, Geo walkSurface, оба коридора, reward level gate, source quote и CP final layer. SCENE072 до account/Player creation. Если нет входов — BLOCKED_INPUT с тремя причинами.
- [ ] 3. Один finite lvl20+ fixture, нулевые Adena/shots: 2–5 native reward kills → earned funds → существующий SupplyJourney paid1463/return → две новые rewarded kills со shot debit. Сохранить snapshots и тот же actor после restart; никакого reseed.
- [ ] 4. CP: fresh finite2000A fixture на проверенной поверхности; COMMITTED5592/physical deltas и native handler usage/reuse/Olympiad guard. PvP: existing owners, actual cause/skill/debit → SUPPLY_REQUIRED → retreat/replan → одна lawful supply attempt. Gear partial если нет earned funds; optional epic≤10min либо reasoned SKIP.
- [ ] 5. Exact source commit → один archive Ant jar compile-tests; focused suites и native evidence из этой сборки. Отдельный RELEASE072,203physicalGeo, ordinary Main≤8min и sameDB/sameJAR restart; штатный STOP, DB/JVM/listeners0.
- [ ] 6. Fresh read-only review, честная acceptance matrix, отдельные mojibake и escaped Cyrillic scans, exact diff --check, exact stage/normal commit/non-force push и remote compare. Отчёт не требует повторной сборки неизменившегося product source.

## Review focus

Reward eligibility до Player; layer после load; earned vs fixture provenance; immutable persisted actor/receipt across restart; wrong owner/port/DB fails closed. Native results определяют PASS/PARTIAL, исторический FULL071=PARTIAL сохраняется.

Ruling: пользователь и AUTHORITY072 уже выбрали автономное выполнение; дополнительных design/plan approval gates нет. Native implementation ведёт основной исполнитель, final reviewer только read-only.

Уточнение владельца: production farm selection допускает мобов выше на1–2 уровня при native reward/viability. Независимый read-only review подтвердил существующий LEVEL_RADIUS2, scheduler suitability/replan и transient SEARCH_LIMIT. Выявлен узкий admission defect: viability без native reward gate. Исправление только CombatPolicy scalar + EngagementPlanner eligible + VisibleAutoPlay ordinary branches; defensive targets и pending-kill semantics сохранены. Focused combat-core55/55. Глобальный fallback после local visible replan (~2км) в текущем owner отсутствует; расширение не включено в072.

Первый native life episode на committed f629e817: actor268480990/profile1 получил3kills20418lvl18, EXP835862→837761/SP0→84/A0→350 и loot1867x1. Затем700-local targets exhausted; исходный FAIL сохранён. Тот же actor продолжается через существующий bounded native walk, без переноса/ресурсов/reset. Scope5000 применяется исключительно к короткому тесту.

Native продолжение довело до8kills, EXP840926/SP224/A893. Выявлен экономический вход: Sword129 расходует3shot/hit, Tyra добавляет50% к14A, полезность пути зависит от native running/walking. Один bounded recovery допускает до12 суммарных реальных kills и останавливается сразу при расчётной достаточности по неизменной supply policy. Новые деньги/бесплатные shots запрещены. Failed child/root/deadlines не сбрасываются.

CP: actor268480995/profile2 сохранён с2000A/9Dshots после premeasurement проверки. Его actual80518,147986,-3504 и Helvetia80518,147922,-3506 дают64.03 по3D; LoS=false. Прочитан native Npc.canInteract и L2jCommerceBackend.validateActor: LoS не является их gate, требуются3D250/same instance/quiet actor. Дополнительный LoS gate теста снят, native guards не изменены; повторной fixture выдачи нет. Failed raw geometry/surface/quote-controls сохраняются. Source quote проверяет funds, budget принадлежит сервису; negative исправлен на реальную нехватку wallet, без product fix.

## Итог исполнения

Сквозной результат PARTIAL: finite12nativekills actor268480990/profile1 дали EXP843443/SP334/A1066, но unchanged SupplyJourney useful-distance policy отказал в1463 BUY. Ни paid return, ни paid-chain restart не заявляются. Повторное накопление после исчерпания bounded recovery не запускалось. P1_CP_PAID PASS: два native paid receipts у Helvetia и handler/reuse/Olympiad. Native PvP остался на preflight: три допустимых Giran field-кандидата не дали route proof, ни противника, ни нападения не создано.

Дополнительные ограниченные product deltas внутри navigation/supply owner families: nearest safe CITY vs RESPawn comparison, подтверждённый Geo floor Giran anchor через tolerance128, CP target25 по уточнению владельца. AA multisell500 реально существует240/600AA; durable phantom operation отсутствует, остаётся optional future gap без календаря/ресурсов/нового source. Focused20use — только contract, не native20use claim.

Build delivery-v3 source5b61a8a, final JAR011D5891..., Ant jar compile-tests41s/exit0; 81focusedchecks, runtimeguardPASS. Предварительные2builds сохранены; всего3wholebuilds, один finalartifactset. Portable ordinal hash устраняет PS5/PS7 sorting disagreement; reparse guard сохранён. Own TEST/RELEASE203physicalGeoassembled; Main/restart и финальный STOP фиксируются в report-data/RUNTIME072.md. Итоговый report и acceptance различают source, fixture, native paid, native earned и незакрытый life chain.
