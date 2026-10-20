# PSS-003 — изолированная подготовка XML и проверка настоящим Java-каталогом

**Проект:** Phantom Semantic Studio, C#/.NET 10 WinForms. Это самостоятельная задача **только** для `kpCat/PhantomSemanticStudio`.

**Required base:** `origin/main` = `60d48348687467c3724ae2bb010ef62987c58d4e` (полный SHA). Репозиторий `https://github.com/kpCat/PhantomSemanticStudio.git`, local root `C:\Users\ZBook\PhantomSemanticStudio\`. Local branch может называться `master`; итог всегда пушить `HEAD:refs/heads/main` обычным non-force push. Проверить удалённый HEAD и ancestry до старта. При любом расхождении не reset/rebase/merge/force, а `BLOCKED_BASE` с read-only отчётом.

**Codex:** основная coding-модель, reasoning **High**. Начать в новом диалоге; не спрашивать повторного согласования для согласованных ниже безопасных действий. Одна задача — один commit/push checkpoint, затем STOP.

## Зачем

Сейчас PSS-002 создаёт JSON-кандидатов и позволяет вручную одобрять их, но экспортирует только REVIEW_ONLY JSON. Пользователю нужен путь к реально расширенному Semantic Pack **без права менять работающий High Five**. В PSS-003 нужен проверяемый мост: **одобренный ограниченный набор кандидатов → отдельная копия 65 humanized-файлов → предложения изменения двух custom XML → отдельная DB-free проверка настоящим Java-загрузчиком**. Это ещё НЕ установочный пакет и НЕ подтверждение поведения в игровом клиенте.

### Главное правило PSS-003

**Весь `C:\Users\ZBook\L2J_Mobius\` и всё `kpCat/L2J` — READ_ONLY без исключений.** Ни приложение, ни Codex, ни Ant, ни Git не пишут в них. Запрещены и новые `build/`, `dist/`, `.phantom-local/`, логи, временные каталоги или JAR в исходном L2J. Любая компиляция Java разрешена **только в физически скопированном и проверенном isolated shadow root внутри собственного workspace Studio**. Никаких symlink/junction в исходный L2J. GameServer, DB, client, runtime Phantom, `ant verify`, Gradle/Maven download не запускать.

## Что делать

1. **Read-first**: изучить `AGENTS.md`, `README_RU.md`, `reports/PSS-002-final.md`, `reports/PSS-002-ui.md`, `reports/PSS-002-build-final.txt`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`, затем все файлы PSS-003. В GitHub сверить actual required base и diff PSS-002. Прочитать `PackReader`, `CandidateValidator`, `CandidateReview`, `ReviewExporter`, `WorkspaceStore`, `PathSafety`, `TextRules`, `MainForm`/Designer и existing console tests; только точечное чтение перечисленных Java/XML-контрактов High Five.
2. **Формировать изолированную staging-копию** в собственном `%LOCALAPPDATA%\PhantomSemanticStudio\workspace\proposals\<unique-id>\module\dist\game\data\phantoms\` (у тестов может быть другой собственный temp root). Копировать *побайтно* ровно список `PackSnapshot.Files`, подтверждать все hash/bytes и актуальность исходного fingerprint до и после. Запрещены copying unrelated L2J files, секреты, БД и пользовательские runtime данные. При ошибке/смене source не публиковать частичную staging-копию.
3. **В первой реализации выпускать предложения только в staging-файлы** `semantic/custom/my-social-topics.xml` (`<socialTopics version="1">`) и `conversation/custom/my-phrases.xml` (`<phrases version="1">`), сохраняя остальные custom-данные и v1/v2/v3/manifest без единого изменения по SHA. Никакого автомонтажа новых сегментов v3 и никакого автоматического `override="true"`. Текст формирует только пользователь/ранее одобренная Gemma; LLM не пишет код/XML.
4. **Hard-fail всей выбранной партии**, если один кандидат не проходит правила в `VALIDATION_RULES.md`. Источник — *точный список выбранных кандидатских ID*, explicit manual approval (`CandidateReview.IsCurrent`) и отдельное явное подтверждение пользователя отсутствия взрослого/матерного/гендерно-специфичного текста и ложных игровых утверждений. При отсутствии подтверждения — только diagnostics, без staging. Никаких молчаливых пропусков, автоперевода Gender в ANY, автокоррекции текста, повышения лимитов.
5. **Минимальная UI-интеграция**: одна статически созданная в Designer кнопка/действие «Создать изолированное XML-предложение» на вкладке экспорта и явное предупреждение `НЕ ДЛЯ УСТАНОВКИ`. Для PSS-003 допустим export всех текущих APPROVED кандидатов только после явного подтверждения общего списка, с блокировкой целой партии при любом несовместимом. Не менять существующую JSON-review функцию и не включать publish/install. Отдельный ручной Java validation — скрипт, не автоматическое исполнение Ant приложением.
6. **Java oracle вне L2J**: отдельный operator/test скрипт запускается только по явной команде на isolated shadow module; копирует разрешённые `build.xml`, `java/`, `test/java/`, `test/resources/`, `dist/libs` в shadow (без symlink; без production DB/secrets) и использует штатный target `ant phantom-humanized-v3-content-validate`. Цель запуска: реально загрузить staging-каталог именно Java-кодом, в том числе custom overlay. Добавить узкую проверку `PhantomHumanizedCatalog.loadV3(stagedRoot, true)` и ожидаемых приростов counters/combined hash, либо явно записать, почему дополнительный Java assertion невозможен. Все Ant outputs/build/reports — исключительно внутри shadow/workspace. Отсутствует безопасное окружение/JDK25/Ant/зависимости → `BLOCKED_JAVA`, не подменять C# PASS.
7. **Обновить AGENTS.md**: отменить прежнее требование создавать `New-ReviewBundle.ps1` и какие-либо **review ZIP для нашего Code Review**. Теперь ревью ассистента опирается на GitHub `origin/main`, exact diff, тестовые журналы и отчёт. **Task ZIP** (этот архив инструкций) — единственный передаваемый пользователем тип архива, он не ревью-архив. Не удалять уже существующую пользовательскую функцию экспорта REVIEW_ONLY JSON в самом приложении без отдельного требования.
8. Тесты, доказательства, отчёт, commit и push. Реальный Java/LM/UI/DPI statuses различать. Никаких «server-ready» слов, даже когда Java content gate зелёный. **STOP после PSS-003**.

## Scope

Ориентир 8–10 исходных файлов: новый `Core/IsolatedPackStager.cs` (и маленький contract model при необходимости), `MainForm.cs`, `MainForm.Designer.cs`, консольные тесты `tests/.../Program.cs`, отдельный `scripts/Test-PSS003-Java.ps1` (только shadow), `AGENTS.md`, `README_RU.md`, при воспроизведённом дефекте — точечный `CandidateValidator`/`PathSafety` с тестом. Reports/docs задачи отдельно. `.sln`, csproj и Visual Studio Designer не переписывать без необходимого точечного обоснования. Никаких новых NuGet-пакетов/SQLite/полного Java runtime в Studio.

## Выход

- `reports/PSS-003-plan.md`, `reports/PSS-003-final.md`, `reports/PSS-003-owned-files.txt` и точные проверочные stdout в `reports/PSS-003-*.txt` без секретов/пользовательского текста.
- Git commit(s) только в Studio, normal push только `https://github.com/kpCat/PhantomSemanticStudio.git` → `main`, удалённый SHA == local HEAD. При BLOCKED сохранить безопасный код/тесты/отчёт и всё равно normal commit+push, если Git безопасен.
- **НИКАКИХ review ZIP, source ZIP, ZIP для ревью или review bundle ни локально, ни в Git.** Сопоставление и приёмка только по опубликованному GitHub.
- Никаких изменений/коммитов/тестовых записей в `kpCat/L2J` или локальном High Five.
