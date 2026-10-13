# CONTEXT041 — что уже есть и чего не следует потерять

## Источники и граница уверенности

Основа: прямые сообщения пользователя в текущем диалоге; восстановленные требования «Ветка · lineage»; опубликованные040 RESULT/GATES/FIX_REVIEW/REAL_FINAL на cff7b7d479cc27413af6f4fdb317d7ced5d51b48; входной пакет040; текущие Agents.md/build.xml и стандарт задач. Ветка на момент подготовки указывает на этот BASE. Локальные raw, службы, БД и ZIP040 здесь не проверены — read-first Codex проверяет только нужные материалы на компьютере пользователя. Не утверждать, что прочитана побайтово вся прежняя переписка.

## Последний результат

TASK040=PARTIAL_VERIFIED; M1=OPEN. Product C4=5d84cbf46cfb97735fe2c7ef4b52e589de0d073b. Последний doc-only commit cff7b7d479cc27413af6f4fdb317d7ced5d51b48. GameServer hash 6DABD52DE07ACF2A9A0C5BEB0510EC737F95302DA104AF46320B05130FD7221E; LoginServer 842DE13026715111D88E0A6EF5AD9727EAEC73E99EB958BFBE0F5ACD325C37D6.

| Gate040 | Состояние по опубликованному отчёту | Что сделать041 |
|---|---|---|
| G0/G1/G2/G7/G8 | PASS; native natural loot обеих ролей; группы8/8 и7/7 по~380s;93/93 и lifecycle11/11 | Не регрессировать; на новом final candidate переснять обязательный final набор |
| G3 | A32/32,B39/39,restart1 40/40 full save/drain PASS; restart2 recorder достиг512MiB без RUN_END | Починить orchestration/capture и получить полный конечный proof; старый incomplete не «долечивать» поздним SQL |
| G4 | actual45s death→legal recovery→useful PASS; productive away→background→native return NOT_VERIFIED | Довести ту же личность через всю цепочку, в том числе законную смерть при её возникновении |
| G5 | Два normal sameDB boot были; native load68/220/363, useful resume220; первый cut потерял original IDs, goal.runtime transitions без producer chain | Полная current chain для исходных и новых identities, реальные изменения целей и rewards |
| G6 | BLOCKED_AUTHORITY;040 не делал crash | Пользователь теперь разрешил исключительно041x72/041x73; до arming ownership/fault map и recovery readiness |
| Observer | actual REAL object268492939 подтверждён; reconnect не измерен отдельно | Новая сборка: автономность, actual REAL/ходьба/instance, logout/fallback/relogin |
| REAL_FINAL040 | FAIL_INCIDENT_DEFERRED, USER_ACCEPTANCE=NOT_GIVEN | Не менять историю; свежая обычная приёмка без Зарича после серверных PASS |

## Уже внесённые полезные изменения

035: реальный inventory cap250 и сохранение существующего overfull без уничтожения вещей.036: native earned lifetime/drain.037: полезный progress/debt.038: action/capability/navigation и один command owner.039: native progress до resource early return, coherent resource snapshot, immutable after-commit witness.040: фактический AutoUse capability/cursor/standpoint, завершение resource tuple после полезной работы, точный capacity handoff, stale-authority CAS ordering, passive observer в существующем LocalPlay worker.

Восьмёрка product040: LocalPlayM1Observation.java, LocalPlayPilotService.java, LocalPlaySyntheticHumanService.java, PhantomSystem.java, PhantomEngagementPlanner.java, PhantomEngagementController.java, PhantomVisibleAutoPlay.java, PhantomHistoricalBackgroundService.java. В041 эти файлы можно причинно дорабатывать, но нельзя потерять перечисленные исправления. Сохранение определяется поведением/тестами, а не запретом менять их hash.

## Прежние ошибки кампаний, не повторять

Только диагностировать и упаковывать — недостаточно. Было много wrapper/input failures, задержка от LASTEXITCODE, проигранные короткие окна наблюдения, перезапуски без полной goal lineage и гигантский039 архив876336 entries.040 review гораздо меньше (~132MiB,255entries в последнем сообщении пользователя), но новый041 не обязан включать его целиком.

Ограниченные завершённые streams и один прямой verifier полезнее второго WAL и recursive proof-платформы. G3/G5 можно проверять на одних и тех же остановках. Если данные исторического эпизода потеряны, честно сохранить UNKNOWN и построить новый полный acceptance set, не объявляя прошлое восстановленным.

## Защищённый пользовательский preview — исторические идентификаторы

Root: C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\.phantom-local\contract040a\runtime.
DB: l2jmobiush5_phantom_test_040a. RuntimeId d41f9ba822d51f3e95487a6d94b55bd85d7e62df4522ecfb7abc64f1e575429d.
Game PID30312/startTimeUtcTicks639274376723876308; Login PID18688/startTimeUtcTicks639274376704922975. Из отчёта: game7777/login2106/control9014; shared MariaDB3308, тогдаPID7704. Не считать PID/запущенность текущими без проверки. При смене incarnation новый процесс всё равно не принадлежит041.

REAL observer в пользовательском Status: modeREAL, actualNetworkSession=true, object268492939, instance0, coords45786/41244/-3504, observerCurrent=true. Это реальная привязка в тот момент, не гарантия всех дальнейших переходов. powershell.exe работает; pwsh не найден.

## Пользовательские наблюдения — не потерять и не раздувать scope

1. В городе/стартовых зонах виден активный бой магов и melee. Иногда после убийства ближайшей цели бот стоит при видимых дальше мобах. Это проверяемый М1 симптом: реальные radius/geo/instance/busy/debt/decision. Не «лечить» увеличением радиуса и не считать дальнего видимого моба заведомо допустимой целью.
2. Не заметны pickup/соски/level-up animation; боты остаются на гремлинах/келтирах. Проверить actual loot, наличие расходников, EXP/SP/level, выбранный existing goal. Отсутствие анимации не равно отсутствию прогресса. Исследовать пригодность существующего target policy, но не строить сейчас полноценную карьеру/магазины.
3. Нет диалогов/ответа на «го пати», реакции на удары, рынка Гирана/.offline/сборов Камалоки; в ряде других локаций бот не найден. Это реальные пожелания/наблюдения будущих этапов, не доказательство отсутствия всего кода и не выполненные функции.
4. HTML paths в системном чате TestAdmin. Отличить штатную GM diagnostics от вывода обычному игроку; найденный operator toggle документировать. Не переделывать все NPC диалоги ради М1.

## Zariche — окончательная граница текущей задачи

Уровень12, cursed8190/transform301; casting=true без task/future, pending MOVE_TO. Вероятная причинная ветка — AURA Void Burst3630 self-anchor в stock PlayableAI cursed PvP guard, ранний return без очистки pre-cast state. Исторический transition/последние network clicks не записаны. Перезаход восстановил движение; пользователь погиб об охранника и явно отложил баг. Никаких новых traces/патчей/повторений041 по Заричу. Не выдавать relog за исправление. Обычные player-control regressions проверяются независимо.

## Направление М2–М7

Все классы/роли, цели и progression, профессии20/40/третья, экипировкаD–S+, PvP/PK/ганги, пати/инстансы/кланы/эпики, торговля/покупка/продажа/крафт/соски/.offline, книги заточки, семантика/чат, мягкая дематериализация. Точное распределение по М2–М7 брать из действующего roadmap, не перенумеровывать из памяти. Реальные пожелания не подменяются списком существующих классов.

Пользователь отдельно требует конечную полностью собранную поставку со ВСЕМИ принятыми плюшками после М7; самостоятельный Start/Stop/Status и конфиги. Ни private worktree, ни report.zip не являются такой поставкой. DELIVERY.md превращает это в сохраняемый проектный контракт уже041.

## Ссылки для точного чтения

Все source references ниже — repository-relative на BASE041:
- docs/phantoms/tasks/PHANTOM-M1-FULL-CLOSEOUT-040/{RESULT040.md,GATES040.tsv,FIX_REVIEW040.md,REAL_FINAL040.md,SCENARIOS.md};
- docs/phantoms/tasks/PHANTOM-M1-OVERNIGHT-CAMPAIGN-039/{RESULT_HANDOFF039.md,EVIDENCE_INDEX039.md,CHECKPOINT039.md};
- Agents.md, build.xml, docs/phantoms/TASK_PACKAGE_STANDARD.md, CODEX_WORKFLOW_CONTRACT.md и действующий master plan.

Результаты ранее приложенного deep-research-report сохранены в040/sources/RESEARCH_ORIGINAL.md и RESEARCH_REVIEW.md; это внешний обзор и прошлые рекомендации, не новый source-level proof041 и не приказ подключить Recast/gdx-ai или headless-клиенты.
