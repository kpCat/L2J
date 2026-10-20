# PSS-008 — quality/coverage → isolated v3 proposal → verified manual release handoff

## Режим исполнения

- **Это третья и последняя из трёх УКРУПНЁННЫХ задач PSS-006/007/008.** Выполнить одну PSS-008 с тремя внутренними gates A/B/C в новом диалоге Codex; основная coding-модель, **reasoning HIGH**, без повторного согласования, затем STOP. При опасном blocker — не обходить, оставить завершённые независимые checkpoints и честный BLOCKED/FAILED.
- Studio root: `C:\Users\ZBook\PhantomSemanticStudio\`.
- Git origin (exact): `https://github.com/kpCat/PhantomSemanticStudio.git`; expected `origin/main` == **`0344c9c0671d53a7bb757db7a92b1dfd03246e9c`**. Пользовательская локальная ветка исторически `master`, сохранять её и пушить `HEAD:refs/heads/main` non-force. На remote drift — BLOCKED_REMOTE, без merge/rebase/reset/force.
- High Five: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`. **ВСЁ `C:\Users\ZBook\L2J_Mobius\` СТРОГО READ_ONLY**, включая .git, Java, v1/v2/v3/custom/manifest, DB/config/geodata/server/client/build/temp. Исходный локальный checkout может не соответствовать публичному reference. Только bounded чтение contract/content/SHA.
- **No review ZIP, no source ZIP, no archive code/real data in Git.** `scripts/New-ReviewBundle.ps1` не запускать. Единственный ZIP здесь — входной task-package. Пользовательский JSON REVIEW_ONLY экспорт Studio оставляем нетронутым. Реальные корпус чатов/SQLite/index/PII/модельный ответ/черновик XML и staging только в ignored workspace.

## Что уже доказано и что НЕТ

PSS-007 published main `0344c9c...` реализовал offline dialogue lab, явного наставника, ручной selected-corpus bridge; отчёт: Release 107 PASS/0 FAIL, STA 7/0, legacy 4/0,3/0,2/0; historical PSS-003 PASS_JAVA_STAGED для **custom**, не для v3. Physical GUI/DPI/VS Designer round-trip NOT_TESTED, LIVE Gemma BLOCKED_LM unloaded, Java PSS-007 NOT_RUN. Это исходные evidence, а не разрешение их наследовать как новые PASS.

Важное различие: `PackPreview` / `DialogueLabSession` — приблизительная инспекция каталога, **не** полная Java parity и не игровой runtime. Нельзя выдавать пройденный Java content-loader за успешные social/persona/functional/PK/PvP runtime cases.

## Обязательное read-first (до edit)

1. `AGENTS.md`, `README_RU.md`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`, `reports/PSS-007-final.md`, `reports/PSS-007-ui.md`, `reports/PSS-007-review.md`, `reports/PSS-003-final.md`, `reports/PSS-006-final.md`.
2. **Все** `docs/tasks/PSS-008/{DESIGN,A_QUALITY,B_V3_PROPOSAL,C_JAVA_RELEASE,SOURCE_CONTRACT,SECURITY_GIT,TEST_CASES,ACCEPTANCE,HANDOFF}.md`.
3. Relevant code: `PackReader`, `Models`, `CandidateValidator`, `StageBatchSelection`, `IsolatedPackStager` (custom baseline, не менять его контракты), `WorkspaceStore`, `PathSafety`, `SemanticDuplicateScout`, `DialogueLab`, `DialogueLabStore`, `CorpusStore`, `MainForm`, `StageSelectionForm`, `DialogueLabForm`, `scripts/Test-PSS003-Java.ps1`, `scripts/java/Pss003CatalogProbe.java`, existing console runner and Designer checks.
4. Source READ_ONLY: High Five `dist/game/data/phantoms/semantic/humanized/v3/manifest.xml`, разрешённые listed segments; точечный `PhantomHumanizedCatalog.java` (`loadV3`, `readV3Manifest`, `readV3Semantic`, `readV3Conversation`, bucket caps), `build.xml` target `phantom-humanized-v3-content-validate`. Прочитать реальные bytes/counters; никаких git/ant/javac/server commands в L2J.
5. `git rev-parse --show-toplevel/HEAD`, status incl untracked, origin fetch/push URLs, `git ls-remote origin refs/heads/main`; snapshot user dirt и task-package SHA. Git только в Studio root.

## Один крупный checkpoint, три independently reviewable gates

**A — прозрачная карта качества пака.** Pure bounded `PackCoverageAnalyzer` или аналог: counts/budgets, group counts по topic/act/band/register, дыры в coverage, exact/lexical repeat candidates, missing response patterns и source provenance. Показать это в нормальном WinForms окне без заморозки UI. Никакого объявления semantic completeness или factual truth. Corpus/chat privacy не нарушать; raw chat and evidence only local. См. `A_QUALITY.md`.

**B — реальный, но изолированный v3 proposal.** Дополнить старый custom-only staging отдельным режимом: пользователь ВРУЧНУЮ выбирает 1–20 CURRENT APPROVED, точную уже подключённую пару `SEMANTIC`/`CONVERSATION` из v3 manifest и подтверждает тексты/смысл. Добавлять только в разрешённые уже существующие segment XML **В НОВОЙ ФИЗИЧЕСКОЙ КОПИИ внутри workspace**; manifest не менять автоматически, новых категорий/act/topic не создавать. Кандидат не подходит единственной паре — BLOCKED_SCOPE, ничего не угадывать. Исходники не меняются и не получают write. См. `B_V3_PROPOSAL.md`.

**C — Java oracle, replay и безопасный handoff.** На physical shadow запускается действующий проверенный Java loader `loadV3(..., true)` и узкий штатный DB-free content target, проверяются новые точные IDs, counts, hashes, отрицательные контроли; отдельно выпускать **только offline folder** с proposed changed v3 XML и оригинальными backup bytes + SHA-manifest/операторским checklist, **только если** exact Java evidence для exact stage PASS и оператор подтвердил. Никаких ZIP релиза/установки/copy в L2J, GUI не запускает Java, Java operator запускается отдельно. См. `C_JAVA_RELEASE.md`.

## Исполнительские правила

- Глубоко, но не бесконтрольно: 3 checkpoints A→B→C, локальные red/green tests, межэтапный read-only review и stage-safe commits допускаются. A не блокировать навсегда из-за отсутствия Java; C может BLOCKED_JAVA, без лживого GREEN. При падении C сохранить безопасные A/B и proof.
- Сохранить VS Designer: стандартные WinForms `*.cs`/`*.Designer.cs`/`*.resx`, constructor только `InitializeComponent()`, без циклов/IO/async/DI/factories/LINQ внутри `InitializeComponent`; no runtime BuildUi. При невозможности physical UI honest NOT_TESTED и ручной чек-лист.
- Не добавлять автоматический выпуск/publish/deploy, background model batch, новый model provider/tools, исполняемые моделью скрипты, изменения Java/runtime/SQL/сервера, auto approve, broad `git add .`.
- Не добавлять данные пользователя/оригиналы corpus в Git/test fixtures/reports. Отчёты содержат только synthetic quotes, агрегаты, публичный код и контрольные суммы. Реальные candidate тексты/stages и операторские shadow proof остаются вне Git.

## Обязательный выход

`reports/PSS-008-plan.md`, `reports/PSS-008-final.md`, `reports/PSS-008-ui.md`, `reports/PSS-008-owned-files.txt`, targeted evidence stdout (без raw corpus), опционально `reports/PSS-008-java-summary.json` только безопасные счётчики/hash без личного контента; updated README. Никаких review ZIP.

Checkpoints A/B/C statuses отдельно, Release `scripts/Build-Verify.ps1` exit/count/zero warning/errors, STA/static Designer, focused negative tests, exact source SHA equality before/after, Java native exits only if actually run; physical UI/DPI/LM separate status. Final exact allowlist/index then ordinary commit(s) + non-force push **только** to `kpCat/PhantomSemanticStudio` main, remote SHA equality, clean tree. In BLOCKED/FAILED — безопасный отчёт/коммит/push, нельзя выдавать skipped за PASS. STOP; не начинать PSS-009.
