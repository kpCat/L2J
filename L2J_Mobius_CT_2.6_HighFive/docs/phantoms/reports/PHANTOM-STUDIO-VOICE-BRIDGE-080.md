# PHANTOM-STUDIO-VOICE-BRIDGE-080

24.10.2026. **Итог: PARTIAL. P0 реализован и проверен в C#/STA; P1 = BLOCKED_JAVA; P2 и SAFETY сохранены. Полная Studio↔Java parity и готовый release handoff НЕ подтверждены.** Приёмка владельцем отдельно. 081/R2 не начинались.

## Authority, read-first и scope

Единственный Git root: `D:/Codex/repos/L2J-phantom-world`, ветка `feature/phantom-world`. Local HEAD, remote-tracking и live remote до изменений и повторно непосредственно перед патчем: `f7676d2032aca6c6204330f5a30fa1bf380974e7`. Tracked dirt отсутствовал. Существовавшие untracked `.phantom-local/` и task packages 068V/073/076/077/078/079/080 сохранены и не включаются в commit.

Прочитаны root/Studio AGENTS, весь пакет 080 (TASK, SOURCE_MAP, PRODUCT_DECISIONS, TEST_PLAN, ACCEPTANCE, DELIVERY, HANDOFF, GOAL, MANIFEST), README_RU/DESIGN_RU Studio, PHANTOM_CURRENT_STATUS, CODEX_WORKFLOW_CONTRACT, OWNER_FINAL_RELEASE_TARGETS, результат079. Родительские AGENTS выше Git root отсутствуют; дополнительных AGENTS внутри целевого дерева не найдено. Read-first карта — SOURCE_MAP пакета; отдельный актуальный pattern-файл не найден и повторно не искался.

Прочитаны реальные модели, CandidateValidator/Review, PackReader, оба stager, V3ProposalContract, WorkspaceStore, SemanticDuplicateScout, V3ReleaseHandoff, MainForm/Designer, StageSelectionForm/V3ProposalForm, PSS003/004/005/008 аналоги и console runner. Прочитаны csproj и Directory.Build.props: .NET10/C#14/WinForms, существующая SQLite10.0.12, без изменения зависимостей/lock-файлов. Переиспользованы CandidateReview.Edit, сохранение версии кандидата, Save/Discard/Cancel, exact selection 1–20, атомарный stager и штатные operator/runner. Отдельной формы/движка/тестового проекта нет.

Bounded exception: один связанный путь Voice проходит через 21 файл, включая этот отчёт. Это одна080 feature, без housekeeping и переработки подсистем. Серверные Java/dist/config/build.xml, OwnerConfig и owner contract — read-only.

## P0 — редактор, approval и V3

- `Candidate.Voice` и `PackEntry.Voice` — необязательная строка, default `""`. Текущий точный native набор Java079: universal/calm/open/direct/empathetic. Это отдельный базовый стиль, не полная личность и не перечень будущих черт.
- WinForms: статические Designer ComboBox в конструкторе и редакторе кандидата; PATTERN отключает выбор, MIXED применяет выбор только к TEMPLATE. Модель по-прежнему возвращает прежний строгий JSON и не выбирает произвольные metadata/code/tools. Голос виден в библиотеке/кандидатах, full staging selection и proposal/proof preview.
- Правка голоса использует прежнее сохранение редактора: несохранённые изменения блокируют ревью сохранённой версии и разрешаются Save/Discard/Cancel перед переходом/staging. Сохранение даёт DRAFT, очищает approval и advisory. Voice включён в approval и advisory fingerprints, peer recheck, XML/receipt и expected-ID hash. Изменение поля в обход редактора делает прежний fingerprint неактуальным.
- Старый JSON без Voice читается как universal. Старый approval не перепривязывается к новой схеме fingerprint автоматически; требуется ручное ревью. Неизвестный/case-mismatched/null Voice и непустой PATTERN Voice блокируются также на границе загрузки/сохранения workspace, до binding ComboBox.
- PackReader сохраняет исходный текст атрибута без Trim, metadata/source line и byte SHA. Voice разрешён только у V3 template, строгий lower-case набор. V1/v2/custom/PATTERN и неизвестные атрибуты в V3 contract отвергаются. Для voiced act сохраняется clean universal UNKNOWN/NEUTRAL fallback.
- Новые receipts имеют Version2 и Voice; legacy Version1 допускается только generic и только после прежней полной проверки source/inventory/append-only. Универсальный authored XML не имеет атрибута `voice`; voiced TEMPLATE имеет выбранный атрибут. Legacy custom stager отвергает V3-only Voice целиком.

Положительный source smoke читал настоящий079 pack: **66 файлов / 5313 patterns / 20995 templates / 26 пар**, source fingerprint `2cb5856c83f52d98e695549f7660a40fcca326ba2a6aaeb924201fca0f210950`. Повторное чтение подтвердило равенство source stamps. В новом private `%LOCALAPPDATA%/PhantomSemanticStudio/workspace/voice080-20261024/workspace` создан только synthetic fixture stage `ecd40212892a426b891a2e5fc2fac99e`: PATTERN + direct FAMILIAR/CASUAL + universal UNKNOWN/CASUAL. Programmatic fixture approval помечен synthetic; это не ручное одобрение владельцем боевых фраз.

Receipt SHA256 `ac5383fd397856b364b6e3805e4c59d55b0a5bc1429a79d47223d0c1182f74b6`. Изменены только две XML-копии внутри stage:

| Относительно staged phantom data | SHA256 | Bytes |
|---|---|---:|
| semantic/humanized/v3/segments/lineage/pvp.xml | e02c88cf0c06c5fc322630ad7e63a96498d59d10bd211775aee9868292f5f9fa | 41025 |
| conversation/humanized/v3/segments/lineage/pvp.xml | 49a8d1b5e633ce80af20b98246b239ce865e2a637623cc4df11d62a04f7d9b6f | 190465 |

Stage integrity: PASS_V3_STAGE_INTEGRITY. Stage остаётся STAGED_V3_UNVALIDATED / Java NOT_RUN / NOT_INSTALLED. XML, stage, реальные proof/logs и ключи не публикуются в Git.

## P1 — точный source audit и BLOCKED_JAVA

Read-only проверены `PhantomHumanizedCatalog.Template.voice`, PERSONA_VOICES, loader V1/V2/V3/custom, validateCoverage и оба `select`. Legacy select делегирует с пустым Voice; voiced pool ограничивается прежними act/band/register/mature gates; отсутствующий/неизвестный runtime voice использует universal, чужой persona не выбирается. Проверены соответствующие079 registrations в PhantomPost002SemanticV3Suite и content mode PhantomTestLauncher.

Точные неизменённые входы текущего HEAD:

| Файл | SHA256 |
|---|---|
| java/.../humanized/PhantomHumanizedCatalog.java | 493d1010e653f66b2e1f3407914735a4ce5a370f8dd7d0d5349fbccb0fb76ef0 |
| build.xml | 13b0144d55d5d1d195b0d859f785e8c9c66dfb22d6190d9f2982a2bef36c7756 |
| tools/OwnerConfig.ps1 | 605ab76de229b3912d3ed5185688e4b847647f2bbd2ee577a1f0cab294854d5f |

Reviewed catalog SHA добавлен в exact allowlist operator и handoff. Wildcard/«любой SHA» не добавлен. Pre079 API теперь явно BLOCKED до исполнения bridge; historical provenance hashes не являются обещанием совместимости с новым probe.

**Блокер — reachable Ant graph:** `phantom-humanized-v3-content-validate → compile-tests → compile,init-test`; теперь `compile → owner-profile-validate,init → owner-config-apply → checkRequirements`. Owner gate вызывает PowerShell `tools/OwnerConfig.ps1 -Action Apply -JavaHome ...`, затем sourcefile OwnerProfileVerifier с `dist/game`. OwnerConfig имеет config/profile transaction, `.phantom-local` staging и temp/backup write paths. Это шире старого copy allowlist, где нет tools/config, и старых task-shape pins. Добавить лишь текущий build SHA недостаточно: удаление owner targets, подмена compile dependency или обход shape/source checks запрещены. Без отдельного bounded контракта безопасных входов и всех reachable writes расширять operator нельзя.

Штатный `Test-PSS008-V3-Java.ps1` вызван **один раз** по новому080 stage: **exit2, `BLOCKED_JAVA: unaudited build contract`, line58**. Отказ произошёл до создания oracle, копирования build/source inventory и запуска инструментов. В stage остался только `module`; **Ant runs=0, Java/JDK probe runs=0, shadow builds=0, HMAC/proof/handoff=NOT_RUN**. Никакой альтернативный Java validator или unvoiced fake PASS не использован.

Probe подготовлен к девяти полям exact spec: ID/kind/act/topic/textHash/target/Voice/band/register. Voiced путь вызывает настоящий `select(...,voice,...)`, проверяет exact ID/text, wrong voice, отдельно band/register, generic/unknown fallback; generic сохраняет прежний overload. Duplicate/schema native negatives сохранены. Handoff требует Voice spec и native voice evidence, разбирает только уже size/SHA-проверенные bounded log bytes. Но эти изменения **source-reviewed, native compile/positive/negative NOT_RUN**, поэтому P1_NATIVE_PARITY и P1_RELEASE_HANDOFF не пройдены. Полная цель080 остаётся PARTIAL.

## Проверки, RED и неудачные попытки

Все пути команд ниже относительно единственного Git root. Console DLL: `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll`.

| Команда / проверка | Фактический результат |
|---|---|
| `dotnet run --project PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/PhantomSemanticStudio.Tests.csproj -c Debug -- --voice080` до production patch | exit1, 0/1; RED XML_ATTRIBUTES на voiced V3 |
| Тот же focused route после реализации | exit0, 3/3 |
| Дополненный focused route на неизвестный сохранённый Voice до review fix | exit1, 2/3; Expected failure, but operation succeeded |
| Тот же route после WorkspaceStore fix; финально Release DLL `--voice080` | exit0, 3/3 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File PhantomSemanticStudio/scripts/Verify-Designer.ps1` | exit0; static, не VS round-trip |
| `powershell ... -File PhantomSemanticStudio/scripts/Verify-PSS008.ps1 -StaticOnly`; PowerShell AST parse operator | exit0; Designer/handlers/resx/nesting и syntax |
| `powershell ... -File PhantomSemanticStudio/scripts/Build-Verify.ps1` | Release solution 0 warnings/0 errors; ordinary runner 122/122; exit0 |
| Release DLL `--pss-008-b`, `--pss-008-c` | exit0, 8/8 и 3/3; те же маршруты также входят в ordinary runner |
| `dotnet exec --runtimeconfig PhantomSemanticStudio/src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json <console DLL> --pss-008-controls` | финально exit0, 3/3, реальные STA controls |
| Release DLL `--pss-008-source <HighFive> <новый private080 workspace>` | exit0; actual source hashes equal; synthetic selected3, Java NOT_RUN |
| Release DLL `--pss-008-inspect-stage <stage080>` | exit0, PASS_V3_STAGE_INTEGRITY |
| `powershell ... -File PhantomSemanticStudio/scripts/Test-PSS008-V3-Java.ps1 -StageRoot <stage080>` | exit2, BLOCKED_JAVA до oracle/Ant/Java |

Негативы охватывают strict voice/case/null, PATTERN/custom, lost universal fallback, stale fingerprint/source, tampered receipt, exact normalized duplicates, XXE/DTD, reparse/source overlap, 21-item selection и fake proof. Используется прежний runner; добавлен один focused route с тремя tests, без нового проекта. Семантические противоречия и выдуманные игровые факты автоматически доказанными не считаются.

Отклонение от желаемого бюджета: первая Release solution build была запущена до завершения review. После двух найденных дефектов выполнена **одна корректирующая Release solution build** с тем же 122/122; затем один test-project Release build для исправления STA fixture. Итого две solution Release builds, обе успешны, плюс одна test-project compile. Ошибок компиляции не было; это не выдаётся за одну сборку.

Независимый read-only reviewer нашёл: (1) invalid saved Voice → пустой ComboBox → silent universal, исправлено на WorkspaceStore boundary, RED→GREEN; (2) чтение probe log до bound/hash, исправлено разбором verified bytes; (3) pre079 catalog/API mismatch, добавлен явный pre-execution BLOCKED. Дальнейших review/fix циклов нет.

Первые STA попытки не дали результата: sandbox запуск отменён (exit1); интерактивный показал source/workspace guard, поскольку новый test fixture был в repository artifacts при первоначальном source=монорепозиторий. Guard был прав, production-защита не менялась. Исправлен только fixture: до Shown указан собственный temporary source, затем обычные controls/Click/persistence. Во время диагностики CUA screenshot timeout; accessibility дал точный текст ошибки. Один chained test-project build/STA вызов завершился exit1 без тестовой диагностики; отдельный запуск той же DLL дал 3/3. Отменённые попытки не считаются PASS. Get-CimInstance в sandbox получил AccessDenied; повтор для собственного runner после Ctrl+C не нашёл процессов. Live Gemma, manual100/150%DPI и VS Designer round-trip NOT_RUN; native gameplay/client/DB NOT_RUN.

## P2 / SAFETY / финальный guard

Сохранены V3 64 files / 32MiB / 8192 patterns / 32768 templates и SQLite/workspace1GiB; миллион XML не импортируется и не объявлен готовым. Выбор1–20, explicit consents, current-source identity, atomic staging, private workspace, XXE/DTD/CRC/size/reparse guards сохранены. GUI не запускает scripts/Java/Ant, сервер не обращается к LLM. Нет новых per-Player библиотек/потоков, фоновых служб, auto-approval/apply/promotion.

Owner final contract не менялся: SHA256 `305ff418d07be303d6a586f17cafd3792294ec89b218eb84b766d7216baaed85`, цели5000/128+/200+ сохранены. OwnerConfig не запускался и не изменялся. Protected release057–061, Playable078/БД, MAIN/PLAY, клиент и чужие worktrees не затрагивались.

- mojibake-маркеры в изменённых файлах проверены отдельным полным набором;
- escaped Cyrillic в изменённых файлах проверены отдельными regex для Unicode/XML escapes.

Обе финальные проверки дали exit0 на всех21 файлах: exact case-sensitive literal набор27 mojibake-маркеров — 0 совпадений; шесть Unicode/XML regex — 0 совпадений. Два предварительных inline regex-вызова PowerShell завершились exit1 без вывода и не считаются проверкой. Предварительный case-insensitive поиск дал пять совпадений на нормальном окончании `р»` в старом README; окончательный поиск точных маркеров с сохранением регистра их не подтверждает. Исходники ради этих false positives не переписывались.

Финальный scope guard — exact allowlist ниже, `git diff --check`, проверка отсутствия иных tracked mutations и exact index перед commit. Чужие untracked artifacts не трогаются. Итоговая post-push проверка SHA/clean публикуется в ответе чата после выполнения команд, без самоссылочного SHA внутри этого commit.

## Exact changed files

1. `PhantomSemanticStudio/README_RU.md`
2. `PhantomSemanticStudio/scripts/Test-PSS008-V3-Java.ps1`
3. `PhantomSemanticStudio/scripts/java/Pss008V3CatalogProbe.java`
4. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/CandidateValidator.cs`
5. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/IsolatedPackStager.cs`
6. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/IsolatedV3ProposalStager.cs`
7. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/Models.cs`
8. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/PackReader.cs`
9. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/SemanticDuplicateScout.cs`
10. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/V3ProposalContract.cs`
11. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/V3ReleaseHandoff.cs`
12. `PhantomSemanticStudio/src/PhantomSemanticStudio.Core/WorkspaceStore.cs`
13. `PhantomSemanticStudio/src/PhantomSemanticStudio.WinForms/MainForm.Designer.cs`
14. `PhantomSemanticStudio/src/PhantomSemanticStudio.WinForms/MainForm.cs`
15. `PhantomSemanticStudio/src/PhantomSemanticStudio.WinForms/StageSelectionForm.cs`
16. `PhantomSemanticStudio/src/PhantomSemanticStudio.WinForms/V3ProposalForm.cs`
17. `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/Program.cs`
18. `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/Pss008.cs`
19. `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/Pss008Controls.cs`
20. `PhantomSemanticStudio/tests/PhantomSemanticStudio.Tests/Voice080.cs`
21. `L2J_Mobius_CT_2.6_HighFive/docs/phantoms/reports/PHANTOM-STUDIO-VOICE-BRIDGE-080.md`

## Git / publication

Git разрешён TASK/DELIVERY080 и прямым запросом владельца. Использованы `git rev-parse --show-toplevel HEAD`, `git rev-parse HEAD refs/remotes/origin/feature/phantom-world`, `git rev-parse refs/remotes/origin/feature/phantom-world`, `git symbolic-ref --short HEAD`, `git status --porcelain=v1` (также `--untracked-files=no`), `git ls-remote origin refs/heads/feature/phantom-world`, exact-path `git diff`/`--stat`/`--check` и index `--cached --name-only/--check/--stat`. Reviewer использовал `git status --short` и exact-path `git diff`. Первое сетевое ls-remote в sandbox не соединилось; read-only повтор с разрешением дал актуальный SHA.

Публикация разрешена и выполняется exact `git add -- <21-path allowlist>`, обычным `git commit -m "PHANTOM-080: edit and stage native persona voice in Studio"`, `git push origin HEAD:refs/heads/feature/phantom-world`, затем `git rev-parse HEAD`, live `git ls-remote` и tracked/full status. Без reset/clean/stash/restore/branch/rebase/amend/force/worktree; master/default branch не меняются. При успешном push local=remote и tracked clean подтверждаются финальным сообщением. P1 BLOCKED не переименовывается в SUCCESS после публикации.

## NEXT PERSONALITY/GENERATION

Дополнение владельца принято как требование **следующего отдельного задания после независимой приёмки080**, без реализации здесь. Нужны десять комбинируемых измерений с интенсивностью, а не десять взаимоисключающих enum voices:

1. Агрессивность, вспыльчивость, конфликтность.
2. Язвительность, сарказм, токсичность.
3. Юмор, весёлость, склонность шутить.
4. Замкнутость, немногословность.
5. Самоуверенность, хвастовство.
6. Робость, неуверенность.
7. Романтичность, склонность к флирту.
8. Грубоватость, склонность к ненормативной лексике.
9. Хитрость, расчётливость.
10. Болтливость, склонность к сплетням.

Эти признаки должны сочетаться с native Social/Personality, отношениями, контекстом, каналом и памятью. Четыре нынешних persona voices сохраняются как базовые стили и fallback; текущее необязательное Voice не становится контейнером/ограничением будущих измерений.

Будущий режим Studio «Автоматическое наполнение библиотеки»: выбор тем/признаков/интенсивности/количества, пакетная генерация через подключённую локальную LM Studio; очередь, остановка/возобновление, resource limits/progress. Нужна проверка точных/близких дублей, противоречий личности, выдуманных игровых фактов и неподходящих фраз, с честными пределами автоматической проверки. Результат только private DRAFT, без auto-approve. После ручного approval — существующий защищённый XML staging и настоящий Java validation. GameServer не обращается к LLM, каждый Player не загружает всю библиотеку. Мат/флирт/взрослый контент требуют отдельных режимов, разрешений и контекста; принудительного мата у всех нет.

Никакие schema/Java scope, массовая генерация, trait runtime или 081/R2 в080 не добавлены. После публикации — STOP.
