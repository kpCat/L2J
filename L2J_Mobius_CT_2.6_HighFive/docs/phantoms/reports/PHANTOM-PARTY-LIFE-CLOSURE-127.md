# TASK127 — PARTIAL / STOP127

Дата: 05.11.2026. Кампания завершена безопасно; FULL127 не достигнут. TASK128 NOT_STARTED.

## Поставка и scope

- Live baseline: `kpCat/L2J feature/phantom-world`, `b5e28324ed7e9b4433a78d326173b01019b20434`.
- Код опубликован обычным non-force push: [`203856715d2f0e30b064a57534a56c8b7729e2d4`](https://github.com/kpCat/L2J/commit/203856715d2f0e30b064a57534a56c8b7729e2d4), parent точно b5e28324. Перед push и после него проверен live GitHub.
- Shipping scope: четыре production файла (`PhantomSystem`, `L2jPhantomSupplyJourney`, `PhantomAutonomousFarmParty`, `PhantomPartyCoordinator`) и два существующих suite (`PhantomPartySuite`, `PhantomSupply071Suite`). Этот отчёт — отдельный scoped commit.
- Независимый source review: **P1=0/P2=0 только для этих шести production/test файлов**. Открытые продуктовые и native gaps ниже не закрыты этим заключением.
- Использован новый physical no-hardlinks clone из live GitHub, без alternates. Первый clone из устаревшего локального branch b4d21 сохранён как setup FAIL и не использовался для разработки/запусков.
- Основной WIP109 `D:/Codex/repos/L2J-phantom-world` read-only; HEAD остался `236b965a116bbc79cccbef78b04c628225a629cb`. MAIN/PLAY, старые TEST/release, Studio, OwnerConfig, rates и исходные Geo не изменялись.

## Исправленные причины

1. До native Join проверяется привязанный `supply.intent`: ACTIVE, paused/expired-unsettled, незавершённый возврат/upgrade и unresolved/INCONSISTENT commerce receipt закрывают Party admission. Их owners/receipts не отменяются и не объявляются завершёнными.
2. Solo arrival продолжается существующим VisibleFarmTravel вне ready-page. До ARRIVED новый состав не формируется. Очередь ограничена 16 bookmarks, продвижение — двумя entries в секунду; discovery остаётся 5 секунд. Цели, эпохи, STORE, deadlines и native consent сохраняют исходные проверки.
3. Supply перепроверяется у requester и invitee непосредственно при consent. Точный single-leader ABORTED first-formation fence ремонтируется существующим absence/CAS; для подтверждённого native Party2 допускается повтор штатного invite при точном roster/root и отсутствии конкурирующего combat/route owner.
4. Независимый review выявил hot-loop SQL в новом continuation. Fixed-clock RED: 76 чтений вместо 12 без продвижения clock. GREEN ограничивает весь путь чтения/arrival секундным окном, не кэшируя разрешение на native consent.

Общий release уже сформированной Party при supply и доказанный групповой маршрут к farm не реализованы. Это остаётся открытым продуктовым P2; небезопасный leave/cancel не добавлялся.

## G0 и изоляция

- Initial BLOCKED_G0 сохранён неизменным. Конфигурация CLI `0.166.0-alpha.6`: Windows sandbox `elevated`; effective permissions — workspace-write/restricted network/auto_review. `/status` и `/debug-config` как slash endpoint через доступный API не предоставлены; прочитаны CLI help/config и фактические токены.
- Default exec: изолированный offline sandbox principal, Medium. В этом контексте диагностические CIM/TCP команды были ограничены. Точные principal/SID сохранены только в private G0 receipt.
- Approved outside-sandbox exec: owner principal, Medium, SID проверен в private G0 receipt. `Get-CimInstance Win32_Process -ErrorAction Stop` и `Get-NetTCPConnection -State Listen -ErrorAction Stop` успешны; проверены полнота inventory, отсутствие Java/игровых listeners и принадлежность SQL listener.
- Полная service-process identity получена штатным UAC/canonical `Invoke-G0IdentityAttestation.ps1`, с pinned одноразовым receipt. WMI ACL, UAC, системные политики и MariaDB service permissions не менялись.
- SQL process: PID8692, `NT SERVICE/MariaDB`, MariaDB11.4.13, loopback3308; SCM/image/command/PID-file/datadir/JDBC связаны. Image SHA256 `103fddd6cb26f4692b2438742d498ec66aa9518a23cbf02dd6f2e3dc7f242de3`.
- Перед CREATE: full grants/metadata visibility, foreign sessions0, schema absent; plan121 stock SQL files/203 statements, 18 historical DROP исключены. Создана только новая `l2jmobiush5_phantom_test_127`, один раз; DROP/reset/reseed не было.
- Собственная stage UUID `17c4c0e2-9489-42fd-8301-46f5e8b95d6a`, port17788, private owner+SYSTEM ACL, физические Geo203 (1063452308 bytes). Исходник Geo: `D:/PhantomWorld/Playable-078-owner/game/data/geodata`, только чтение.
- Fresh G0 выполнялся перед каждой native фазой; предыдущие STOP/postflight обязательны. Проверены 2234 protected preimages, включая WIP/config/JAR/Geo scope. GUI выключен только в собственной stage; gameplay/rates не менялись.

## A–F: фактический результат

| Gate | Итог | Native evidence / ограничение |
|---|---|---|
| A logistics | PARTIAL / OPEN | В Native1 supply реально принят, куплены 20×1061, затем `supplies.native.returned_to_base`; Party/farm arrival и group-route не доказаны. |
| B rewarded PvE | NOT_ACHIEVED | Native Party3, native damage/kill/reward/loot/EXP/SP witnesses =0. Условие ≥2 before +≥1 after на финальном JAR не выполнено. |
| C Heal | NOT_EXECUTED | Learned native Heal и PRE-CUT wound были подготовлены; настоящего native heal cast/HP+MP outcome нет. |
| D defense | NOT_EXECUTED | B/C не выполнены; разрешённый human hostile command не выдавался. На restart human locality не доказана. |
| E PK/karma | NOT_TRIGGERED | PK/karma0; native PK не форсировался. |
| F restart/logs/safety | PARTIAL | Clean STOP/STORE/postflight; selected SQL continuity подтверждена. Party/PvE restart и старый buff/knowledge-hash incident не закрыты. |

Первый native sample лидера уже DEAD, level54, HP0, MP826, SP4020, Adena8810 при native counters0; PRE-CUT было level55/SP0/wallet5000. Historical catchup за время подготовки изменил контрольную сцену до наблюдаемой materialization. Эти виртуальные результаты не засчитаны как native PvE.

PRE-CUT HPstock20 означал item1540 Quick Healing; production supply использует NPC buyOffers + `isHealingPotionSkill`/HP_RECOVER и купил1061. Прежний readiness marker дал ложную уверенность в production supply readiness. Внешние исходники preparer/validator исправлены по точному production predicate и скомпилированы отдельно; после CUT fixtures не переигрывались и native stage classes не заменялись этими изменениями.

В Native3 profile2 наблюдался устойчиво (239 samples), profile3 кратко (1 sample); profile1 не materialized. Для1/3 сохранены `ecology_fenced`/sleeping-background admission. Calendar ACTIVE сам по себе не доказывал устойчивую native готовность всей тройки.

`ROUTE_BUDGET_EXCEEDED` найден в сохранённом `supply.reason.native_refusal` перехода к NPC (80265,53333→79530,54894); позже supply завершился. Это не доказательство свежего terminal отказа farm-route. Конечный farm handoff остаётся недоказанным.

После Native3 тот же бесполезный путь прекращён. Для продолжения не подменялись ecology/admission/Party/цели, не выдавались средства, не телепортировались и не лечились акторы.

## Запуски и log audit

| GameServer | JAR SHA256 | Окно UTC | Результат остановки |
|---|---|---|---|
| Native1 | `36b41dc4c2da85fd7ee5e6090b238e4ef3e99ef85d289dc3a33b2f6b9359625a` | ready05:53:30 → shutdown05:57:36 | real exit0, STOP/STORE и postflight PASS; productWitness=false |
| Native2 | тот же | ready06:04:44 → shutdown06:04:50 | observer FAIL; healthy STOP/STORE, observed postflight PASS; `game2.exit0` отсутствует |
| Native3 | `8109af42142f4879f5830bd4e52ee14294be667ecb4e148b337e56088770ed8f` | ready06:18:34 → shutdown06:22:40 | real exit0, STOP/STORE и postflight PASS; productWitness=false |

- Прочитаны raw stdout/stderr и полный stock `java0/error` каждого запуска. Побайтовые SHA-архивы сохранены до следующего запуска; review подтвердил135/164/207 архивных копий, mismatches0. Старые rotated logs различены от current `java0`.
- В current stock server logs Native1/2/3: ERROR/SEVERE/Exception0; WARNING52/4/52. Причины: optional `data/buylists/custom` отсутствует; LoginServer9014 намеренно не запущен; штатные предупреждения собственного shutdown. Ничего не подавлялось.
- Native2 raw stderr содержит один исходный `NoSuchFileException` observer: отсутствовал `human-route-own127view.tsv`. В Native3 проверка отсутствующего prepared route явно возвращает NOT_EXECUTED, пишет причину в stderr/receipt и не выдаёт movement/XYZ/GK команд. Исходный FAIL сохранён, acceptance Native2 не заменён PASS.
- PRE-CUT bootstrap также записал два SEVERE `deleteMe() called on offline character` для временных human. Причина — неверный lifecycle harness; future source приведён к `setOnlineStatus(true,false) → store/delete` по существующему headless pattern, compile-only PASS. Повтор подготовки после CUT запрещён; исторические сообщения не сняты.
- Initial setup failures PSModulePath/двойной URL-comment/no-overwrite snapshots и неудачная fixed-spawn гипотеза сохранены. Они не объявлены GameServer product regressions. Fixed spawn не найден; этот путь прекращён.

## Conservation и incident

- `stored1 → before2`: все82 выбранные не-UTC SQL строки равны. `stored2 → before3`: различий0. Это точный cohort subset трёх запросов, не заявление о равенстве всех таблиц сервера.
- Оригинальные managed character IDs сохранены:268480990/268481014/268481037. Собственная population автоматически создала четвёртый profile; stock offline-trade startup заполнил200 merchant rows. Чужие схемы не использовались.
- Каждый STOP: drained=true, stopFailed=false, retained materialization/navigation/combat/actor leases/owners0. Postflight: characters offline, own/foreign SQL sessions0; отдельного LoginServer/JVM не осталось. MariaDB service продолжает работать.
- MaxHP3033→2946 у лидера и3313→3217 у DPS наблюдались вместе с55→54 после historical death. Это объясняет данную смену maxima, но **не** старый TASK126 buff3321/3033 + knowledge-hash gap.
- Старый active-buff/hash incident в текущих raw launches не воспроизведён; его root cause остаётся UNKNOWN/OPEN. Player/EffectList patch TASK126 не переписывался, hash/LOAD guards не ослаблялись. Финальный JAR имеет только один завершённый native window; успешный final sameJAR gameplay pair отсутствует.

## Проверки и публикация

- Focused RED→GREEN: supply-before-Join, late requester/invitee, exact refused formation/Party2 retry, pending arrival/root/STORE/native takeover и bounded SQL cadence.
- Итоговый seed17001701: party-lifecycle26/26, party-state-recovery14/14, party-tactics10/10, party-route7/7, supply07110/10: **67/67**, exit0. Одна fresh Ant JAR сборка на каждый из двух кандидатов, обе exit0; JDK25. Broad `ant verify` не запускался.
- Source/JAR parity: exact six source SHA из `candidate2.json` совпадают с tested stage; финальный GameServer.jar совпадает в source и stage. После native3 production/test код не менялся.
- Final exact allowlist: шесть code/test файлов + этот отчёт. Протокол двух одинаковых проверок после privacy scrub: `verify127-3.json` и `verify127-4.json`; protected2234/Geo203, scope, source/JAR и кодировки проверяются отдельно от gameplay. Предыдущие receipts1/2 сохранены как проверка предыдущего драфта.
- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0.
- escaped Cyrillic в изменённых файлах проверены: все6 regex, совпадений0.
- У `PhantomSystem` исходный mixed EOL сохранён. Whitespace gate использует `blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol`; обычные проверки не отключены. Global fsmonitor давал false-clean, поэтому status/diff выполнялись с `-c core.fsmonitor=false`.
- Git разрешён явно TASK.md §11. Использованы read-only `rev-parse HEAD`, `rev-parse HEAD^`, `symbolic-ref --short HEAD`, `status --short/--porcelain=v1 --untracked-files=all`, exact-path `diff/--check/--cached`, live `ls-remote`; clone inspection/config для physical source и fsmonitor. Reset/clean/stash/rebase/merge/force не применялись.

Точные команды публикации (CWD `E127/source`, path list также в `code-publication127.json`):

```powershell
$P=@('L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java',
'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/commerce/L2jPhantomSupplyJourney.java',
'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java',
'L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java',
'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java',
'L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomSupply071Suite.java')
git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world
git -c core.fsmonitor=false add -- @P
git -c core.fsmonitor=false diff --cached --name-only -- @P
git -c core.fsmonitor=false commit -m 'fix(phantoms): settle solo preparation before party admission' -m 'Retain unresolved supply ownership, continue native farm arrival before joining, and recover exact refused farm formations. Bound continuation reads to the existing one-second control-plane cadence. Cover supply/consent/arrival and bounded polling in focused regressions.'
git rev-parse HEAD
git rev-parse 'HEAD^'
git push origin HEAD:refs/heads/feature/phantom-world
git ls-remote https://github.com/kpCat/L2J.git refs/heads/feature/phantom-world
git -c core.fsmonitor=false add -- L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-PARTY-LIFE-CLOSURE-127.md
git -c core.fsmonitor=false commit -m 'docs(phantoms): record TASK127 partial native closure'
git push origin HEAD:refs/heads/feature/phantom-world
```

## Локальные evidence и STOP

`E127 = %USERPROFILE%/.codex/visualizations/2026/11/04/01a1a8a1-3a5a-7890-9094-8aad2a8b18ab/task127`.

- Source: `E127/source`; stage: `E127/Phantom-Party-Life-127-17c4c0e2-9489-42fd-8301-46f5e8b95d6a`.
- `logs127/run1..3`, `log-audit1..3.json`, `candidate1/manifest.json`, `candidate2.json`, `candidate2-helper-source/`, `verify127-*.json`, `G0-PROTECTED-PREIMAGE.json`, `code-publication127.json`. Original BLOCKED report/receipt сохранены отдельно без переписывания.
- Protected preimage UTC04:16:40; code publication06:38:36. Продолжение заняло около3 часов с диагностикой/подготовкой; искусственного ожидания8–10 часов не было. Полный token total недоступен; старый blocked goal counter147569 не выдан за расход продолжения.
- Открыто: native A farm/group handoff; B/C; D/E; полноценный final sameDB/sameJAR gameplay restart; старый knowledge-hash incident; надёжность исходной PRE-CUT control scene. Report PARTIAL не является manual/product PASS.

**STOP127. TEST127 и evidence сохранены холодными. TASK128 NOT_STARTED.**
