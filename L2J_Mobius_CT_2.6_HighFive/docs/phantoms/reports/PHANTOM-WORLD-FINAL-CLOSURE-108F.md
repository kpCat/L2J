# PHANTOM-WORLD-FINAL-CLOSURE-108F

Статус: **FULL108=BLOCKED / PRE_G0_CAUSALITY_UNKNOWN**. Решение `SHOULD_LAUNCH=NO`: сохранённые evidence108R3D не устанавливают причину World5 и достаточность исправления observers для World16. Выполнена предусмотренная TASK108F §4 / TEST_PLAN T0 остановка до G0. Новых native A/B=0/0; runtime, SQL, force, retry, reseed, DDL и manual SQL не выполнялись. TASK108 не закрыта; TASK109 и другой suffix не создавались и не запускались.

## Read-first и scope

- Прочитаны все восемь файлов task-folder108F, корневой и модульный AGENTS.md, README.md, релевантные разделы PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, RELEASE_CONTRACT, TASK_PACKAGE_STANDARD и master-plan, report108R3D. Статусный документ отстаёт от D; приоритет у текущего task и фактических receipts.
- AGENTS.md выше Git-root и в docs/phantoms/tasks/task-folder не найдены. SOURCE_MAP.md задачи найден и использован; отдельные новые code-map/pattern-файлы не требовались. Повторного исторического аудита074–108 нет.
- Прочитан текущий Native.addObservers / World-loop, CohortChecks.capture, Lane, D wrapper/process test и build.xml; locality/admission/topology/ecology и canonical skill-learning owners, DwarvenFighter.xml. Аналоги: D private index, cohort/STOP/postflight receipts, exact-path delivery. Использованы существующие контракты чтения и hash-проверок.
- JDK25/Ant, единственный Java Semantic Engine, обычный Player и канонические ecology/learning owners сохраняются. Studio/UI, public schema, schedule/calendar, caps, rates, OwnerConfig, Geo, profiles и merchants не менялись.
- До изменений tracked dirt0, base→HEAD и tracked working diff High Five пустые. `git status --short --untracked-files=all` обнаружил 321631 доступный untracked path; были предупреждения Access denied на защищённых private folders. Это не полный inventory содержимого этих folders. Чужие файлы сохранены.
- Единственные публикуемые изменения: исходные восемь документов `docs/phantoms/tasks/PHANTOM-WORLD-FINAL-CLOSURE-108F/` и этот новый отчёт, всего9 paths. Семь входных SHA из SHA256SUMS.txt совпали до работы. Исходники, configs, старые task packages, D wrapper/receipts и отчёты не изменены. Нового Task108FRun.ps1, sidecar, authority или stage нет.

## Свежий Git baseline

30.10.2026: `HEAD == refs/remotes/origin/feature/phantom-world == live refs/heads/feature/phantom-world == 58c76fc1e7f02bfe8e019c72b521284f56946c6c`; ветка `feature/phantom-world`, upstream совпадает, единственный worktree — текущий checkout. Merge-base с указанным base равен этому же SHA. Task-folder до работы был untracked, существующие пользовательские файлы не заменялись.

Первый sandbox `ls-remote` завершился128 из-за недоступной сети. Разрешённый escalated `git fetch origin feature/phantom-world` и последующий `ls-remote` завершились0, parity подтверждён. Защищённые evidence и OS census прочитаны через escalated read-only доступ без изменения их ACL.

## Причинный аудит D

Источники разрешены через старый `.phantom-local/logs/PHANTOM-WORLD-RESTART-CLOSURE-108R3D/index.private.json`; абсолютные private пути и installation/campaign UUID в Git не помещаются. Ни один старый receipt не повышен до PASS.

| Сохранённый факт | A108R3D | B108R3D |
|---|---|---|
| World first/second/stable | 16/16/16 | 5/5/4, FAIL |
| Реальные observers в final | 2 | 1 |
| Physical demand / facts в final | 411/411 | 5/5 |
| Aggregate admission final | eligible0/admitted0, pendingRebalance=false | eligible0/admitted0, pendingRebalance=false |
| Native offline OPEN | 250: BUY63/SELL162/MANUFACTURE25 | 250: BUY63/SELL162/MANUFACTURE25 |
| Наблюдаемый exit | 0, 29.10.2026 17:47:12.4652432Z | 0, 29.10.2026 17:59:59.7278452Z |
| Acceptance | A clean сохранён | stop4.natural/postflight4.pass.json/exit4.clean отсутствуют |

1. **Подтверждённый harness defect, не исправлялся после решения STOP:** Native.addObservers резервирует home region до nullable topology lookup. Один неудачный кандидат исключает следующих кандидатов того же региона. Кроме того, setup допускает до16 observers, проверяет лишь непустую locality и может быть пропущен при уже достигнутом World cap. Это не доказательство единственной причины World5.
2. **Уточнение freshness:** B plateau17:58:47Z и final17:59:23Z содержат `pendingRebalance=false`. Manager.admissionSnapshot вычисляет его как `_admissionDirty || !current`; поэтому эти конкретные нули не объясняются только stale ecology. Однако успешная A также имела fresh zero admission. Срезы разных owners не являются общей атомарной транзакцией.
3. **Почему fresh zero сам по себе не доказывает production defect:** существующий physical preparation путь допускает READY/calendar-online demand без объявления ecology eligible; native-context foreground handoff предусмотрен текущими System/Ecology owners. И A, и B имеют `ecology.preparation_capacity`. Ни schedule, ни calendar, ни eligibility не подстраивались.
4. **Точный первичный blocker:** нет сохранённых outcomes отклонённых observer candidates/regions и paired scheduling revision/permitted facts. CohortChecks.capture отбирает только World + physical IDs (до64; в B всего5). A411/B5 и observers2/1 поддерживают гипотезу locality/setup, но не доказывают, что единственный mark-order fix даст достаточную законную physical admission. Production root cause остаётся UNKNOWN; минимальный production diff не обоснован.
5. **SP остаётся отдельным незакрытым gate:** независимое парное сравнение B frames подтвердило ровно одну строку SP312→2 при class/base class53, level5 и EXP3507 без изменения. В DwarvenFighter.xml одинаковые310 SP стоят141:1,142:1 и172:1. Нет paired character_skills и уникального SUCCESS/commit receipt; canonical transaction может завершить SQL commit, затем вернуть DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED. Ни конкретный skill, ни отсутствие двойного debit, ни требуемая формула SP не доказаны. `negativeRows={sp=1}` не ослаблялся. Новая текущая skill SELECT не восстановила бы прошлый before-cut.

## Проверенные evidence hashes

Пути первой группы относительно D stage `private/receipts108R3D/`:

| Файл | SHA-256 |
|---|---|
| world6.pass | 9fc314acf36709834e54dd88f8e465b2f8c85d0438670ed1be4a89531e9b30d5 |
| postflight6.pass.json | 96f58d87f8d4400a5a649e11ef6578bf8c4a5018d3418bfd4a39f58010b52464 |
| exit6.clean | 2bde72812133dc6f8501ee565000e9a39700a24c2b5432cafa719e865bb7e277 |
| world4.fail | 7fd2b5579ceced25a940745b24f522373dbb9345a9937a684aa3e9557fbc3318 |
| cohort108R3-4-plateau.private.tsv | 5823ec264f699f93ff75def4ce702a0c1cc28a9838367f18bbf8e3d1239528e2 |
| cohort108R3-4-final.private.tsv | 8ee28eff836fcf1578ab15450222eee5ecffc11f2c8fbf606f3ca828f29773ee |
| cohort108R3-6-final.private.tsv | d7acd1f232d2f73668949c99acd05a1464c8dbb485d6ed3b33c25c6ac9bf1e43 |
| readonly-afterB/delta105.log | 31d793b88c72b1793ff0863a47f6792d5ac1fbe1478cfe762d6b9fcd9a73f41a |
| exit4.observed | 5bd965c5cfe07898e238f802a7301f49e8dbb383c8f942354d4e4bebf162e960 |

Независимый evidence review сверил все20 A postflight pins, current production2380/2380 и protected184/184. SHA manifests: production `917d283541b8fd2818fe6c51a3051c2aba5f42e32d492b82ab286a5720f2ccf4`, protected `761584c8ccfa0c7ed1ddb8a121a061ed042ccc1376030df81fe64c21524a6a4e`.

Сохранённый A postexit→beforeB exact SHA `c3918f3bf66137fdfc9e542cc8308475084e452fac4844d511d38f6f32ee7e8f`; обе B postexit preimages `036bff45b95fae570ad7b474ad4022b45dabf81ad79ca35d587ce1a82f342b3d`. B native/readonly checkpoint SHA `3ad9d823a6070513082077a14c238b37af8eb4c0875968006316220be8e5fe53`. Эти исторические совпадения не являются новым G0.

## Gates и проверки108F

| Gate | Результат108F |
|---|---|
| Source/branch parity и task hashes | CODE_VERIFIED, exit0 |
| SHOULD_LAUNCH | NO / BLOCKED: причинная достаточность не установлена |
| G0 installation/SQL authority,5000/250,stable preimages | NOT_RUN; gate BLOCKED до нового признанного baseline |
| H1 mark-order regression / два observers | Defect CODE_VERIFIED; NOT_FIXED / DB_FREE_NOT_RUN / NATIVE_NOT_RUN |
| H2 admission cause | UNKNOWN; freshness уточнена, production defect не доказан |
| S1 paired skills/unique debit/earned credits | UNKNOWN в старой B; новый sidecar/negative fixtures NOT_RUN |
| Новая A World/economy/STOP/exit/postflight/clean | NOT_RUN |
| Новая B continuity/World/economy/STOP/exit/clean | NOT_RUN |
| Compile-tests→jar, focused Java/process tests | NOT_RUN: ранний STOP, code changes0, нового stage нет |
| Delivery | Только9 документов; UTF-8, отдельные text checks и staged diff --check exit0; source/evidence review поддерживают STOP; final bytes закрепляются перед commit/push |

30.10.2026 05:08:21Z read-only OS census: Java processes0 и established TCP к MariaDB3308=0, exit0. Sandbox CIM/TCP сначала вернули Access denied; escalated read-only повтор успешен. Свежие SQL sessions/foreign SQL/full visibility **не проверялись**, G0/quiescence PASS не объявляется. Старые B leases/exit receipts подтверждают historical shutdown; это не current SQL census.

Применён `systematic-debugging`: факты отделены от гипотез, без speculative fix. Проверки завершения следуют `verification-before-completion`. RED→GREEN не заявляется: никакой реализации после причинного STOP нет. Это не отказ из-за approvals и не разрешение на ещё одну попытку.

## Private index, review и публикация

Новый audit-only index: `.phantom-local/logs/PHANTOM-WORLD-FINAL-CLOSURE-108F/index.private.json`; owner+SYSTEM ACL, отдельный archive для сводки, проверок и delivery. Campaign authority/seal не создавались. Полные старые raw logs/SQL остаются в исходном D archive; identities/accounts/UUID/credentials в Git не копируются.

Независимые read-only reviewers `/root/review_108f_evidence` и `/root/review_108f_source` согласовали pre-G0 STOP и проверили отчёт: P1=0/P2=0. Уточнение evidence reviewer про недостаточность fresh zero для вывода о production defect внесено. Source reviewer не имел прямого доступа к D и явно использовал observations evidence reviewer; это не вторая независимая проверка всех raw D файлов. Final report SHA и scope review сохраняются отдельными private receipts перед публикацией. Проверка отчёта не принимает native gates.

Git разрешён прямой командой пользователя, TASK108F и root AGENTS для baseline/scope и одного scoped commit/обычного push. Выполненные формы: `git fetch origin feature/phantom-world`; `git rev-parse --show-toplevel HEAD refs/remotes/origin/feature/phantom-world`; `git symbolic-ref --short HEAD`; `git status --short --untracked-files=all`; `git worktree list`; `git for-each-ref --format='%(refname) %(objectname) %(upstream)' refs/heads/feature/phantom-world`; `git merge-base HEAD 58c76fc1e7f02bfe8e019c72b521284f56946c6c`; `git ls-remote origin refs/heads/feature/phantom-world`; `git diff --name-status 58c76fc1e7f02bfe8e019c72b521284f56946c6c -- L2J_Mobius_CT_2.6_HighFive`; exact-path `git diff --check`.

Финальная публикация ограничена перечисленными9 paths: exact `git add -- <paths>`, `git diff --cached --name-only/--name-status/--stat/--check -- <paths>`, полный exact-path staged diff и `git status --porcelain --untracked-files=no`; один `git commit -m "phantom(task-108f): record causal pre-G0 stop"`, затем `git push origin HEAD:feature/phantom-world` без force после свежего parity. Exact список9 файлов и команды сохраняются в private delivery. При stage Git предупредил о будущем LF→CRLF по локальной настройке; входные working bytes и SHA256SUMS не изменялись. SHA commit и фактический push/parity фиксируются в private delivery receipt и финальном ответе: собственный commit SHA нельзя встроить в этот же commit без второго изменения.

- mojibake-маркеры в изменённых файлах проверены: полный набор27, совпадений0, UTF-8 strict9/9, exit0.
- escaped Cyrillic в изменённых файлах проверены: все6 regex, совпадений0, exit0.

Первая inline PowerShell проверка текста завершилась parser error до чтения файлов: передача буквальных markers через shell исказила command text. Повтор с теми же27 markers, построенными по числовым code points только внутри диагностической команды, завершился0. Кириллица в документах не заменялась escapes.

После доставки — STOP. Полный108, World128 в LIVE/PLAY и завершённость Phantom World не объявляются; новой кампании, TASK109 или следующего suffix эта публикация не разрешает.
