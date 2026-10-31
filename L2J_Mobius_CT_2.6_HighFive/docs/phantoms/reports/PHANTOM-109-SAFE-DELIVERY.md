# PHANTOM-109-SAFE-DELIVERY — PARTIAL

31.10.2026. FULL109 остаётся PARTIAL. Проверено независимо собираемое подмножество pickup safety и earned STORE; C2 и инициативный PK/karma в поставку не входят. Это завершение подтверждённой части TASK109, не новая разработка109 и не TASK110.

Baseline: `bd912892b127dfbe4e484ea99ac8df9e19fc4270`, `feature/phantom-world`, `origin=https://github.com/kpCat/L2J`. Live remote совпал перед публикацией. Кандидат материализован read-only `git archive` в отдельной owner+SYSTEM области без worktree. Все остальные 2374 production Java-файла совпали с baseline.

## Точный scope поставки

- Player: stock pickup использует существующий native write boundary с frozen Party recipients до ground monitor. Generic delayed guard не ослаблен.
- BackgroundService/Transaction/Authority: factual admission lineage, earned STORE и SELECT-only SQL witness с обязательным распространением SQL/owner/epoch ошибок. Combat разрешён только для native persistence; scalar simulation остаётся запрещённой. Новый допуск supported-goal nonzero karma исключён до optional fallback. Прежний stock STORE при отсутствующем/неподдерживаемом goal сохранён.
- NativeWork/NativeEvidence: только default-null диагностические callbacks, необходимые existing pickup contracts; не выбирают получателей и не меняют awards/admission.
- TEST: существующие9 pickup contracts,3 focused риска Party/window, inventory mismatch и unmanaged/mixed Party; STORE contracts выделены из существующего109 checker без Party/PK dependencies.
- Два документа: этот отчёт и короткий handoff. Bounded exception:11 exact paths =6 production +3 TEST +2 docs, одна атомарная поставка.

Party coordinator/backend/autonomous farm, PvP context/service, social/bridge и farming остаются baseline. Полный acceptance harness не включён: его compile на baseline показал4 ошибки зависимостей от новых social/PK API. Три исходных harness-файла сохранены отдельно; production для удовлетворения этих зависимостей не расширялся.

## Проверки и границы

1. Все2065 файлов final-J index SHA256 совпали до и после работ. Index SHA256 `6d7e8f9b43d11329870c48089bd83de3f2a5387ffa4688c317696b0f5a9c6f4e`. Все38 исходных WIP сохранены побайтно; внешние preimage проверены SHA256. Исторические TEST109–j/JAR/DB/receipts не изменялись.
2. Отдельный `ant ... jar`:2380 production sources, exit0. Финальный последовательный `ant ... compile-tests jar`:exit0; существующие compiler warnings сохранены. Raw social API errors не скрывались: полный harness исключён, после этого final compile-tests прошёл. Точный первый compile diagnostic содержит4 errors и5 warnings.
3. Targeted RED: Party/window, mismatch unsafe и supported-goal karma получили ожидаемый exit1. GREEN: те же3 checks, existing9 pickup и STORE lineage4positive/7negative + persistence-context checks exit0.
4. Один final affected regression:11 запусков exit0 — compile focused pickup,3 focused risks, existing pickup9, STORE contracts и5 existing DB-free suites. Итоги existing suites:
- SUMMARY: suite=background-model seed=15001501 total=10 passed=10 failed=0
- SUMMARY: suite=combat-core seed=20260725001 total=68 passed=68 failed=0
- SUMMARY: suite=party-lifecycle seed=17001701 total=22 passed=22 failed=0
- SUMMARY: suite=party-tactics seed=17001701 total=9 passed=9 failed=0
- SUMMARY: suite=pvp-policy seed=25002501 total=2 passed=2 failed=0

5. Независимый source review `/root/safe_subset_review`: P1=0/P2=0 в точном six-production scope; финальные SHA pins проверены. Отсутствующие runtime dependencies проверены независимой сборкой и полным сравнением production с baseline.
6. TEST P2 repaired copies: counter привязан к measured Party/window/exact owner+epoch; inventory mismatch latch unsafe проверен RED→GREEN; final assertSafe поставлен после drain перед clean marker в отдельной копии Runtime.close и проверен source review. Последний пункт native не запускался. Исправленный raw acceptance harness сохранён вне поставки; исходный WIP harness неизменен.
7. Ordinary Player проверен actual stock entry с non-item target и mixed Party без Phantom owner/DB; это не human-client inventory proof. Pickup competition/transfer/cancellation negatives используют честные contract sentinels. SQL failure contract не открывает реальную БД. Новый кандидат native НЕ запускался.

Native J остаётся историческим bounded evidence: stock inventory13+6, earned STORE/SQL и sameDB/JAR E/F. Exact original nested earned hit→CANCEL native NOT_OBSERVED, только DB-free RED→GREEN. J выполнялась с полным WIP; её scene success не выдаётся за native прогон изолированного delta. Новые TEST109k, TEST-БД, кампании и TASK110 отсутствуют.

- mojibake-маркеры в изменённых файлах проверены:27 markers, совпадений0.
- escaped Cyrillic в изменённых файлах проверены:6 regexes, совпадений0.

## Сохранность и Git

Публикация разрешена пользователем только после dependency-clean gates. Используется отдельный index и exact11-path add/commit; рабочие WIP файлы не заменяются. После commit основной index синхронизируется только по этим11 paths; сохранённые WIP будут честно отображаться как незакоммиченные отличия от опубликованного subset. Новые report/handoff/standalone STORE test разрешено материализовать, поскольку исходных WIP файлов на этих путях не было.

Git reads: `rev-parse --show-toplevel HEAD`, `rev-parse HEAD`, `symbolic-ref --short HEAD`, `status --short --untracked-files=all`, `status --short --untracked-files=no`, `status --porcelain=v1 --untracked-files=all -- <WIP38>`, `for-each-ref --format=... refs/heads/feature/phantom-world refs/remotes/origin/feature/phantom-world`, exact-path `diff`, `ls-remote --heads origin refs/heads/feature/phantom-world`, `archive --format=tar --output=<external>/baseline.tar <baseline> L2J_Mobius_CT_2.6_HighFive`. Broad initial status обнаружил множество прежних untracked artifacts и ACL warnings; затем использовался exact scope. Эти файлы не трогались.

Final Git writes/checks: `read-tree <baseline>` только с внешним GIT_INDEX_FILE; `--work-tree=<candidate> add -- <exact11>`; `diff --cached --check -- <exact11>` и `diff --cached --name-only`; `--work-tree=<candidate> commit --file <external-message>` один раз; `ls-tree -r HEAD -- <exact11>`; `update-index --add --cacheinfo <mode,blob,path>` только exact11 для синхронизации основного index без working-file writes; обычный `push origin HEAD:refs/heads/feature/phantom-world`. Эти необходимые операции разрешены текущим /goal для одного scoped commit/non-force push. Ни reset/stash/rebase/branch/worktree/force, ни изменение master не выполнялись.

Команды с подставленными exact paths, exits, stage SHA, commit/push receipts и полные logs находятся в защищённом delivery archive; приватный указатель `.phantom-local/task109-safe-delivery/delivery.path`. Этот отчёт фиксирует проверенный candidate перед публикацией; фактический commit/push указывается отдельным final receipt и в финальном ответе, не предвосхищается здесь.
