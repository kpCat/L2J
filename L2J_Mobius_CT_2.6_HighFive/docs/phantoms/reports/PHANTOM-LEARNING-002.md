# PHANTOM-LEARNING-002 — результат технической проверки

**READY_FOR_MANUAL_ACCEPTANCE — ТЕХНИЧЕСКИ ГОТОВО в указанном V1 scope.**
**Финальный manual gate REQUIRED; агент не отмечает его passed.**
Runtime завершён, дальнейшие source changes не планируются. Literal Codex UI close
**NOT_RUN**; D1 подтверждён квалифицированными техническими lifetime/native proofs.

Base `b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8`, отдельный clone,
ветка `wip/phantom-learning-002`. Original checkout/исторические установки read-only;
исходный пакет из 13 файлов сохранён. Публикация — только разрешённый scoped commit и
обычный non-force push в эту WIP. Точный final commit/independent GitHub HEAD и реальные
Git-команды приведены ниже; точный HEAD сообщается после независимого readback при доставке.

Git используется только в независимом clone по прямому разрешению владельца. В исходном
пользовательском checkout Git-команды не выполнялись. Точные команды поставки ниже;
`$paths` — каждый из 68 exact путей `SCOPE_ALLOWLIST.txt` с префиксом модуля.
Финальный HEAD после обычного push проверяется отдельно и указывается в сообщении доставки:
commit не может содержать собственный вычисленный SHA в своём тексте.

```powershell
git clone --single-branch --branch wip/phantom-learning https://github.com/kpCat/L2J.git D:\Codex\worktrees\phantom-learning-002-source
git rev-parse HEAD
git symbolic-ref --short HEAD
git status --porcelain=v1
git status --porcelain=v1 --untracked-files=normal
git status --porcelain=v1 --untracked-files=all
git switch -c wip/phantom-learning-002 b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8
git ls-remote origin refs/heads/feature/phantom-world refs/heads/wip/coordination-130-preserved refs/heads/wip/phantom-learning refs/heads/wip/phantom-learning-002
$paths = @(Get-Content L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/SCOPE_ALLOWLIST.txt | ForEach-Object { 'L2J_Mobius_CT_2.6_HighFive/' + $_ })
git diff -- $paths
git add -- $paths
git diff --cached --check -- $paths
git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check -- $paths
git diff --cached --name-only -- $paths
git commit -m "feat(phantoms): add autonomous TEST training director"
git push --set-upstream origin HEAD:refs/heads/wip/phantom-learning-002
```

Read-only diff до staging также ограничивался конкретными production/test seams:
PhantomCombatService, LearningCastEvidence, LearningPolicy, LearningStore,
PhantomLearning001ActorFixture, PhantomLearning001ShadowNative, PhantomTestLauncher.
Все эти exact пути включены в `$paths`. Никаких force/reset/clean/stash/merge/rebase
или изменений master/default/защищённых веток в этой поставке нет.
Обычный whitespace check вернул2 из-за сохранённых CRLF; финальная проверка с
`cr-at-eol` вернула0, сохранив проверки trailing spaces/blank EOF/space-before-tab.
Входные документы и игровые исходники ради line endings не переписывались.

Все evidence paths переносимы от root clone: **R = .phantom-local/learning002/train/**.
Private SQL credentials/datadir, GeoData и исходные runtime logs не публикуются.
Public artifact path указан отдельно ниже.

## Итоговая gate matrix

| Gate | Техническое evidence | Граница утверждения |
|---|---|---|
| D0 — ownership/Git | Own clone/base/WIP; exact allowlist68 и SCOPE_GUARD.json, outside=0 | Фактические commit/push/HEAD сообщаются при доставке; self-reference SHA здесь не выдуман. |
| D1 — автономность | WMI generation7 после parent exit без control: >=2 новых COMMITTED за 1015715 ms; modeled own kill-job closure GREEN | Квалифицированный technical proof; буквальное закрытие Codex UI NOT_RUN, manual gate REQUIRED. |
| D2 — native | Реальные Solo и stock autonomous Party; 117 exact NATIVE keys; cold ON/version=1 и reorders обоих scope | Два offensive skills,class 94,77/78, A/S, NPC 21798/21799; не общий игровой AI. |
| D3 — генерация | 14 actual Solo scenes и 12 Party scenes, реальные cohorts/equipment variants и reasoned skips | Подготовка каталога не равна успеху боя; плохая сцена не получает fake SUCCESS. |
| D4 — история | 34 COMMITTED/35 INCOMPLETE,117/117 unique keys; frozen dataset и final portable export | INCOMPLETE credited events0; без native receipt reward/loot/spoil остаётся UNKNOWN. |
| D5 — обучение | Автоматические DRAFT rejection и ACCEPTED_TEST v1; fresh independent AUDIT; cold exact model readback | Малый audit, особенно Solo1 actor; без обещания DPS/generalization/production improvement. |
| D6 — управление | Double START same owner; PAUSE/RESUME/STOP, stopped REPORT/EXPORT, idempotent STOP и own cleanup | Startup timeout P2 исправлен, focused/actual regression GREEN; финальная ручная приёмка отдельно. |
| D7 — recovery | Controlled own native crash EFFECT→COMMIT, INCOMPLETE recovery, no duplicate credit | Проверен native-process crash; Windows reboot/SQL crash не заявлены. |
| D8 — безопасность | Own full125-script SQL, G0/loopback/identity guards, Geo203/protected207; 277/277 Java и focused PS GREEN | F7 standalone PASS, SQL_REPLAY=NOT_RUN; не обещание полной безопасности всех игровых режимов. |
| D9 — эффективность | Реальные native startup/selection/CPU/RAM, отдельный SQL resource trace и CPU boundary idle | Per-attempt малые выборки; pooled native/system peak UNKNOWN;128-native load не проверен. |
| D10 — доставка | Canonical accepted model ZIP + manifest/dataset/episodes, source/scripts/config/runbook и docs готовы | Разрешённую Git публикацию/HEAD root фиксирует по факту доставки. |

Task docs: `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/`.
Штатный operator runbook:
`L2J_Mobius_CT_2.6_HighFive/tools/phantom-runtime/training/RUNBOOK_RU.md`.
PROGRESS.md содержит журнал команд/exit codes; публичные snapshots собраны в artifacts/evidence/PROOF.json.

## Native опыт и окончательный SQL readback

`R/status.json` на **2026-11-11T14:31:40Z**:
34 COMMITTED,35 INCOMPLETE,117 exact events,attempts2,accepted1,nextOrdinal69.
`R/final-sql-readback.tsv` подтверждает117 events/117 distinct keys и
**incomplete_credited_events=0**.

| Scope | COMMITTED | Exact events | Learner actors | Навыков | Actual scenes | Exact HP |
|---|---:|---:|---:|---:|---:|---:|
| SOLO | 18 | 54 | 18 | 2 | 14 | 98777.5 |
| PARTY | 16 | 63 | 30 | 2 | 12 | 89132 |

Суммирование actors/scenes разных scope не даёт число уникальных участников кампании.
Фактический learner scope — class 94,уровни77/78,gradesA/S,только templates21798/21799.
Существуют реальные pre-CUT cohorts generations0/1/2 и equipment enchant варианты.
Metadata-only ordinal seed не обновляет AUDIT; старые actors/rewards/manifests сохранены.

| SQL policy_version | Scope | Exact events | Exact HP |
|---:|---|---:|---:|
| 0 | SOLO | 48 | 86371.5 |
| 0 | PARTY | 54 | 74501 |
| 1 | SOLO | 6 | 12406 |
| 1 | PARTY | 9 | 14631 |

Cold ON loaded v1 с exact SHA; native.err содержит реальные
`LEARNING_EXACT_REORDER` baseline1230:28→actual1339:1 в Solo и Party.
Последний новый Solo,ordinal68: event
`efcfd66647273091bc5430e055134e2d924caf283ac58502c05332e1f819ef41`,
skill1339,1503HP,policy1. Final SQL TSV также связывает ordinals64/66 с native keys.
Direct HP не равен Party total каждому участнику и не доказывает прирост DPS.

## Обучение и модель

Attempt `ce212360fa6d44b6b3149b70b28719c9` честно остался DRAFT: Solo quality не прошла.
Attempt `d01e95f0233549acb85bf5c358189559` принят автоматически в own TEST.
Worker `R/workers/54a4808248a249cb88035b87064528d6/` завершил evaluator exit 0
12:54:16.685Z под parent director39536; readback позже13:12:23Z:
`R/accepted-model-1-readback.json`. Trainer вручную не запускался.

- Accepted model 1,algorithm 2,step 47,weights12500 bytes, training 33 ms.
- Model SHA256 `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`.
- Frozen dataset SHA256 `202623c277aec76aadafdf3b203a48660461f0e0789b926ef37d102e121a2d77`.
- TRAIN 33 / TUNING 45 / AUDIT 9,auditFresh=true,minimum relative improvement1%.
- Prior MSE=null: это первая accepted model; сравнение с прежней ACCEPTED неприменимо.

| Scope AUDIT | Rows | Actors | Model MSE | TRAIN-constant MSE | Quality |
|---|---:|---:|---:|---:|---|
| SOLO | 5 | 1 | 0.0124465712 | 0.0394474331 | PASS |
| PARTY | 4 | 2 | 0.1025303627 | 0.1397164245 | PASS |

Final SQL подтвердил оба immutable BLOB по12500 bytes/algorithm 2:
DRAFT SHA `bb61a8a0b98e730ca2012259ea8f2ddb590234bef07bb41e43c31201ad418fc9`
и accepted SHA выше. Последующий COLLECTING относится к следующему AUDIT и не отменяет v1.
unsafeActionRate/nativeActionReorderingRate остаются null; проценты не выведены из counters.
Малый audit не доказывает обобщение на другие классы/зоны/NPC; MAIN promotion отсутствует.

## D1, startup/recovery и команды

Главный D1 elapsed source — **`R/unattended-proof-generation7-utc.json`**:
13:51:15.177020Z–14:08:10.8920054Z,1015715ms (~16.9min),caller absent,
controls NONE; COMMITTED31→33/events114→116.

| Ordinal | Episode | Outcome |
|---:|---|---|
| 64 | c282612d66a74b6187cb7f2106dc3b0c | SOLO COMMITTED/NATIVE_EVIDENCE |
| 65 | fee7e74a576b4c5882e677e63dc913ea | PARTY INCOMPLETE/NO_EXACT_NATIVE_EVIDENCE |
| 66 | 1c3b10355dc547189b2a39acd2d1b7ce | SOLO COMMITTED/NATIVE_EVIDENCE |

Неверный diagnostic elapsed8214966ms исключён. Original diagnostic сохранён unmodified;
corrected JSON связывает source SHA9388239deaf22c7c103bd57888b67e749bfcf079a2a3ff389b2ab800f4143066,
literal UTC и DateTimeOffset. SQL/native facts не изменялись.

Modeled own KILL_ON_JOB_CLOSE fixture GREEN13:41:53.7477083Z:
helper26044 held exit; final83376/creation=134388781090449568 вне OWN job до close,
9 новых heartbeat после close иnormal exit 5,nonce26d1f41ab15a4ce1b38d144a86b47e08.
Proof: `%TEMP%/Training002JobLifetime-ecec9e8152d7458888cdfc9a757647a0/own/lifetime.before.json`
и `lifetime.after.json`. AnyJob сам по себе не различает shared non-kill job и caller
kill-job; query scope CURRENT_IMMEDIATE_JOB_ONLY. **Literal UI close NOT_RUN.**

P2 startup 15 s timeout/orphan исправлен bounded ACK+durable pending STOP.
Actual Test-StartupHandshake GREEN: noACK expiry,cancelAfterACK,pendingExitConfirmed2,
duplicateRejected2,SQL0; fixture `%TEMP%/Training002Startup-172ef47195914f82b63a8841b10782c6/`.
Broker6 negatives/JobLifetime+9/Config/EmptyRecovery/Isolation203207 exit 0 по root;
final independent source review — no P1/P2.

Actual generation8 double START exit 0/sameOwner,nonce38db0b7589fd43ce89ad435d4f9537ef.
PAUSED 14:26:59Z с NativeExited=true; RESUME exit 0 в ту же generation8/ACTORS.
Первый final STOP exit 0:
`R/commands/stop-confirmed.fa2435a292e340498350140697c551a7.json`,
14:29:29.4312167Z. Stopped cold REPORT и Test-Export exit 0 сохранили117/model 1;
Isolation203/207 exit 0. Idempotent STOP exit 0:
`R/commands/stop-confirmed.e4272a169e844b98855888a7953ad2be.json`,
**14:31:52.8466956Z**. Own director/native/worker/SQL отсутствуют; ports33327/17971/19271 free.
Default source/root config `episodesPerBatch=6` восстановлен и подтверждён.

Controlled crash T08/T14:
`R/crash-probe.14b2d4887e3b4d92b3591378a6a4533a.json`,`.after.json` и
`R/crash-recovery.14b2d4887e3b4d92b3591378a6a4533a.json`.
В12:06:35Z было60 committed keys и3 raw EFFECT без ledger membership; один attested own
kill дал exit-1. Та же SQL сохранилась, automatic retry старый ACTIVE перевёл в
INCOMPLETE,pending membership0; после retry65/65 distinct. Final117/117 и credited0
подтверждают отсутствие повторного learning credit. Windows reboot/SQL crash не заявляются.

## Canonical публичный экспорт

**Текущий public artifact**:
`L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip`
и `README_RU.md` рядом. Это byte-exact final cold export
`R/exports/model-1-0f302f513b1a4fc785d74ba54ce23a01.zip`.

- ZIP69127 bytes,SHA256 `10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da`.
- weights.bin 12500,manifest.json 1315,dataset.jsonl 107930,episodes.jsonl 533504 bytes.
- Weights SHA unchanged: `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`.
- Manifest ACCEPTED_TEST,origin NATIVE,productionPromotion=false; schema/catalog/dataset SHA сохранены.
- Предыдущий66089-byte экспорт сохранён private и во внешнем backup,но не является current canonical ZIP.

## Измеренная эффективность

Реальные cold-ON attempts,без pooled quantiles.
Evidence: `R/attempts/<attempt>/{native.out,native.err,native-resources.json}`.

| Attempt | Startup,s | JVM elapsed,s | CPU,s | Peak working set,bytes |
|---|---:|---:|---:|---:|
| 70ef76c18d8f4e959ab23b59b875720b | 37.857 | 344.073 | 105.234375 | 4503318528 |
| ee9acef6d25042dd806f3a0039beccb7 | 34.993 | 498.481 | 110.453125 | 4525056000 |
| 49197dba07084648b7c76a20da2caced | 33.151 | 618.414 | 113.437500 | 4540940288 |
| 965dc6d440274cd3965ab7455043bf46 | 35.080 | 349.567 | 92.078125 | 4654895104 |

| Attempt prefix | Scope | Samples | p50,ms | p95,ms | p99/max,ms |
|---|---|---:|---:|---:|---:|
| 70ef76c1 | SOLO | 1 | 2.4233 | 2.4233 | 2.4233 |
| ee9acef6 | PARTY | 4 | 0.2042 | 1.4702 | 1.4702 |
| 49197dba | SOLO | 1 | 0.4124 | 0.4124 | 0.4124 |
| 49197dba | PARTY | 4 | 0.2000 | 1.5941 | 1.5941 |
| 965dc6d4 | SOLO | 1 | 2.3349 | 2.3349 | 2.3349 |

Это full features+selection в native path,но малые выборки,включая n=1; не полноценная
оценка tail latency под нагрузкой. Final965 writer:1batch/1offer,dropped0,total/max17.1534ms.
Отдельный предыдущий c2607e73 имел0 selections/0credit; его latency UNKNOWN,не ноль.
Working set не равен heap; pooled native/system peak UNKNOWN.

SQL38572: `R/sql/sql-resources.134388731218807766.json`,
12:18:41.880Z–13:33:58.795Z,4516914ms,CPU16.4375s,peak154066944bytes,
workingSet=null,exitObserved=true. Это отдельная SQL process metadata.

Final coverage window10:45:33.924Z–14:31:40.728Z,13566803ms,с idle/pauses/diagnostics:
Solo4.776 episodes/h и14.329 events/h; Party4.246 и16.717. Это проверочный интервал,
не обещание длительного темпа. Прежний0.488/h исключён из-за исправленной JDBC timezone.
Measured CPU boundary idle есть; RAM/GPU throttling и 128-native load не заявляются.

## Проверки и границы поставки

Root final Java 277/277:266 existing+11 focused training. Existing:Learning4,Combat72,
ownership37,Party13,coordination77,PvP14,Support9,Supply11,Commerce15,F814.
F7 standalone PASS,SQL_REPLAY=NOT_RUN. Команды,исправленные diagnostic invocations
и exit codes — в PROGRESS.md. Independent review не заменяет эти реальные запуски.

Current build binding `R/build.json`: GameServer JAR
`b1e69a773cec5e9fcdbf99480eab16498678bd61c88a834370bef151c3f92ee5`;
source fingerprint39970f0e40bc2f29879a30bcedcd6908a67c75bb3104f43640087da7fcbe02f8.
Installer125 scripts SHA550d5180472a989cef9526ad4c2a1dfc7fe677633561d2089bc6cab2ff438741.
JAR SHA не является Git commit; final HEAD сообщается по фактической доставке.

128 native visible и обучаемые PvP/PK,Raid,Siege,TW,Olympiad,Summon,Heal,Control,
Spoil/Sweep,Move — **NOT_IMPLEMENTED**. Stock Heal не является trained healer-policy.
Успех в узком TEST не означает MAIN promotion,общий игровой AI или прирост DPS.
**Manual gate REQUIRED; literal Codex UI close NOT_RUN.**

Final scope — 68 exact путей в SCOPE_ALLOWLIST.txt; SHA67 файлов и outside=0 закреплены
в SCOPE_GUARD.json (сам manifest исключён из собственного хеша). Входные13 файлов
защищены узкими -text правилами по существующему паттерну .gitattributes. Приватные
SQL credentials, datadir, Geo и runtime logs в поставку Git не входят. Точный GitHub
HEAD указывается в сообщении доставки после scoped commit/push и независимого readback.

- mojibake-маркеры в изменённых файлах проверены; совпадений нет.
- escaped Cyrillic в изменённых файлах проверены; совпадений нет.
