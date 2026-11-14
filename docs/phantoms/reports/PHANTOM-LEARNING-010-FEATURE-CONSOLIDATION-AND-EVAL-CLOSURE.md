# PHANTOM-LEARNING-010 — консолидация и EVAL

**CONSOLIDATION — VERIFIED. EVAL_CLOSURE — PARTIAL010_NATIVE_EVAL_COVERAGE_FAILED.** Игровой verdict: **INCONCLUSIVE**, малое n; превосходство DRAFT14 не доказано.

ЭтапA опубликован merge `8752d88a8a963b5e283e0ecd0f69c6c319ac5e62` с parent `7e997636dbac9d5e1754e00c793cd6fac858b0c6` и `01e04537eec0a4f6b19236c4e547df7b2a22efb5`. Затем восемь product paths исправлены в `6c97ee43d25acc37b42599b4e5ef138ea0980f4a`; дополнительное bounded покрытие target points — `ccd93cb61dae106aaf596cd41dd2e0b5dc6302ee`; fail-closed native window guard после последнего запуска — `add152c6a114166a98bf41416ed6b09ff8b6e2b9`. Все commit/push прямо в feature, без force. Основной dirty checkout7e997636 сохранён; новый baseline доступен в существующих004/006. master/default branch не менялись.

## Gates

| Gate | Итог | Доказательство/ограничение |
|---|---|---|
| G0 | PASS | 5checkout/worktree, свежие refs/stash/reflogs,490596dirty/untracked и288682ignored source/docs сохранены с SHA; бинарные/runtime данные оставлены на месте. |
| G1 | PASS | Merge8752 с обоими parent и cleanup commit; обычный push, независимый GitHub readback доB. |
| G2 | PASS | JDK25/Ant targeted009/008/007, JAR и DB-free004/005/006; learning defaultOFF; исторические blobs не перекодированы. |
| G3 | PASS | Cap-before-lawful исправлен; три headless pre-CUT receipts с2направлениями, frozen unseen source provenance. |
| G4 | FAIL | Последняя кампания: 0completed pairs; AB=0,BA=0; направления . Первый и второй отрицательные прогоны сохранены. |
| G5 | PASS | 183SQL lineage rows byte-identical; DRAFT14/Adam903/223/48applications, acceptedTEST2 и pointer12 сохранены. |
| G6 | PASS | OS kernel exit по original held handle; STOPPED, owned0,3ports free, активные modesOFF, Geo203/protected207 PASS. |
| G7 | PASS | 3/3GS, 601015/2700000мс;00910/5803240неизменно. CPU/RAM/headless стоимость отдельно. |
| G8 | PENDING_READBACK | Source8752/6c97/ccd93 опубликованы и проверены; финальные документы ещё требуют push/readback. |

## Реальная EVAL

| Кампания | Lawful cells | Settled arms | Completed pairs | Факты |
|---|---:|---:|---:|---|
| `autonomous009-goal010-20261114` | 4 | 8 | 1 | SOLO_DAMAGE:MATCHED_COMPLETED; PARTY_DAMAGE:MATCHED_INCOMPLETE; PARTY_HEAL:MATCHED_INCOMPLETE; PARTY_DAMAGE:MATCHED_INCOMPLETE |
| `autonomous009-goal010-20261114-b` | 0 | 0 | 0 | NO_LAWFUL_SCENE / до CUT |
| `autonomous009-goal010-20261114-c` | 4 | 0 | 0 | INVALID_COMBAT_REQUEST после CUT / reserved1 |

Первый прогон: 15с окно,8arms, только Solo pair completed. Party DAMAGE дала реальные платные касты/HP/MP, Party HEAL — useful heal, но5TIMEOUT и1NO_LEGAL_TARGET; они не засчитаны. Второй: headless4cells, live0cells;1584NO_ISOLATED_TARGET_POINT и84target-bound refusals, CUT0. Все отрицательные данные сохранены. Это ограничение snapshot→live, не compile failure.

Перед третьим прогоном объявлены DAMAGE/180с, тот же frozen14 и прежние safety guards. По фактическому отказу live-каталога добавлены bounded геометрические точки внутри прежних2000, без ослабления faction/Geo/LOS/aggro. Живой каталог нашёл4сцены, но первый reserved Solo arm после CUT упал:180с превышают unchanged combat request max120с. Это ошибка выбора оператором параметра010 и неполный headless contract. Settled arms0, completed pairs0; чужих/повторных training credits нет. После исчерпания3starts Java/PS config отклоняет180с доGS (focused RED→GREEN и Ant PASS); native этого последнего guard NOT_RUN. Четвёртого старта нет.

Последняя кампания содержит 0completed matched pairs; AB=0,BA=0. Всего010 доказана только1Solo pair первого запуска; Party/сбалансированные AB/BA не закрыты. G4=FAIL, FULL010 запрещён. Headless и native receipts разделены. Stock/stock control в010 NOT_RUN: текущий узкий route сопоставляет STOCK/LEARNED; дополнительный control protocol не вводился на последнем разрешённом старте. Исторические controls007/008 не выдаются за010.

Legal choices получены из actual acquired rank/enchant и target-bound eligibility; конкретные native choices/классы/NPC/HP/MP/kill/terminal hashes находятся в EVIDENCE. Независимость от F TRAIN подтверждается pre-CUT source/physical provenance, outcomes TRAIN не использованы. Обе стороны имеют новые actor/target incarnations и равный native risk. Paid effects считаются только по canonical CUT→terminal timing. Whole-party damage, consumables, loot, adena и полный death count остаются UNKNOWN там, где канонического writer proof нет; UNKNOWN не заменён нулём.

## Время и ресурсы

| Старт | Attempt | GS PID | Exact native мс | CPU с | Peak working set bytes |
|---|---|---:|---:|---:|---:|
| 1 | `e74666622ddc4a9f9bf5069f34e46871` | 70208 | 265981 | 247.3125 | 4724805632 |
| 2 | `157f05adcad04acfb4f337a2cb4fc8bc` | 65488 | 221044 | 349.84375 | 4711239680 |
| 3 | `d2beb2ea4af14e0baa8b634ab5755b48` | 38568 | 113990 | 235.21875 | 4699721728 |

Итого010: **3/3GS**, **601015/2700000мс = 10.016917/45мин**, от kernel boot до kernel exit. Observation time отдельно, proof OWN_HELD_HANDLE_EXIT_TIME. Суммируются все неудачные/успешные запуски; между кампаниями перенесены prior1/265981 и prior2/487025. Четвёртого старта нет.009 остаётся10starts/5803240мс=96.720667мин против90, точный старый exit UNKNOWN.

Отдельно headless/SQL-probe/export workers после baseline: 7 JVM, суммарно377486мс процесса, CPU770.718750с, peak4651352064bytes. Это сумма длительностей workers, не native GameServer charge; Ant/DB-free проверки и время backup в native budget не входят. Полный per-worker receipt в EVIDENCE. Все JVM ограничены существующим cpuCount4/maxHeap4096, active actors≤5, memory budget8192MB. Финальный free disk указан в postflight evidence.

## Frozen state и безопасность

Read-only SQL SELECT before/after:183строки, SHA `9A9C2D0EE9A2B8F37DA5DD6BCF625334B76BE96FE9B76E22490ED20B4DA70E7D`, равны побайтово. Проверены все model weights/metrics hashes, Adam optimizer blobs/updates/epochs, advantage snapshots/applications и terminal applications. DRAFT14 weights `7d3c8fc1f2ae5fc199cfb7517f71743189fdd3fbb4b959a318753637e2d3bc37`, Adam903/223,16gradient episodes/48applications сохранены. Fit010=0, новых gradient/credits/model version нет. Cold exports before/after сохранены с CRC/SHA; ZIP timestamps могут различаться, immutable blobs сравниваются отдельно.

Accepted TEST2 не заменена, production promotion=false. Исторический completed-cycle pointer остаётся model12 и не переносится вручную на workingSQL14. 3716старых campaign/pointer/manifest файлов совпадают с before SHA; TRAIN_CONFIG исключён из этого сравнения как явно изменяемый input, его версии сохранены отдельно. MAIN/PLAY, releases/protected207, Geo203 и foreign mysqld8692 от28октября сохранены.

Финально STOPPED, owned Director/Native/Worker/SQL=0, ports33357/18001/19301free. Активные режимы/экспериментальные exploration/fault flags OFF, game LearningModeOFF/model0/evidencefalse/seed0. Inactive evaluation-only/budget safety flags оставлены для fail-closed защиты; cumulative010 prior не обнулён. Raw SQL/weights/optimizer/Geo/секреты в Git не добавлены.

## Scope и проверки

Переиспользованы общий acquired scene planner, ordinary Player/Party admission, ProductionAuthorityFixture, canonicalrisk и held-process receipts. Восемь product files, два workflow docs и14task/report/roadmap docs — bounded exception по явному TASK010; точный allowlist в EVIDENCE. UI, runtime Combat/Party/Player, модели/optimizer/schema не менялись. Root task/report расположен по фактическому выданному пути; roadmap/workflow остаются в module docs.

JDK25.0.4.1/Ant1.10.17: A197с, B145с, C166с, targeted009/008/007 и JAR exit0; D80с autonomous/JAR после native-window config guard exit0. A дополнительно learning001/action-ownership/humanized-v3 и10DB-free004/005/006routes. Focused RED/GREEN на lawful-before-cap, config parity, risk, STOP timing/cadence, bounded placement и180с rejection. PS parser0errors. `ant verify` с DB gates не запускался. Выполненные команды/exit codes и диагностические отказы перечислены в COMMANDS. Независимый code review выполнен,6Important исправлены в bounded pass; дополнительный placement fix проверен targeted regression/native, последний window guard — native NOT_RUN.

- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0.
- escaped Cyrillic в изменённых файлах проверены: все6regex, совпадений0.

Git-команды использовались: явно разрешены TASK010/GIT-POLICY и владельцем для аудита, merge, exact scope, commit/push/readback. Точные команды и исключения находятся в COMMANDS. Branch/stash/worktree не удалялись. Эта задача№4/13; после её выдачи9до пересмотра бюджета. Следующий Goal не запущен. Итоговый review владельца не объявлен принятым автоматически.

[Task evidence](../tasks/PHANTOM-LEARNING-010-FEATURE-CONSOLIDATION-AND-EVAL-CLOSURE/EVIDENCE.json), [приёмка](../tasks/PHANTOM-LEARNING-010-FEATURE-CONSOLIDATION-AND-EVAL-CLOSURE/ACCEPTANCE.md), [команды](../tasks/PHANTOM-LEARNING-010-FEATURE-CONSOLIDATION-AND-EVAL-CLOSURE/COMMANDS.md).
