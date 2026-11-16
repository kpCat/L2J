# PSS-PROMPT-LAB-QUALITY-RECOVERY-001

Статус: техническое восстановление и сравнительные прогоны; качество FULL не заявлено. Ручная оценка владельца REQUIRED. Computer Use не применялся.

## Причина исходного отказа

Исследован сохранённый `DESCRIPTION / REJECTED / 102f83c6f1e44199a2c9707d1d35b63b` из экспериментального workspace. Сначала прочитаны request, полный Content Gemma, transport и outcome; до этого новых запросов не было. HTTP 200, завершённый streaming, контекст 65536, 5630 входных / 785 выходных / 0 reported reasoning tokens, 31.7337 с. В ответе указан несуществующий ключ `lineage.craft.enchant.fail`, `newCategories` пуст. Действующий ключ — `lineage.enchant.fail`.

`ParseProposal` передавал категорию через `PromptLabPlannedBatch.ToRequest()` без преобразования. `V4GenerationRequest.Validate()` правильно отклонял её, но сводил категорию, режим, sampler и transport к общему `GENERATION_REQUEST`. Авторская схема позволяла строковый ключ без доказанной связи с реестром. Автоматическая подмена могла бы изменить смысл и не применялась.

## Изменения

- Authoring проверяет точные значения и типы полей, отсутствующие/лишние/повторные ключи с путями и ожидаемыми значениями. Существующие predicates в V4 не ослаблены; исходная составная ошибка разложена по полям.
- SYSTEM/USER проверяются перед созданием версии в тех же границах storage. Предложение, которое не может быть сохранено, не объявляется структурно верным.
- Одна явная коррекция DESCRIPTION через существующие очередь, receipt и BaselineRunId. Исходный ответ неизменяем; исправление требует проверки плана. Повторный запуск не создаёт новую попытку и не повторяет terminal/неопределённый POST.
- В истории показаны полный ответ и текущая диагностика; ошибки описания открывают историю. Кнопка исправления находится в Designer; I/O и async остаются в основном .cs.
- Сохранён точный модельный ответ как regression fixture; проверки выполняются существующим console runner.

Дополнительно воспроизведён локальный `CONTEXT_BUDGET_REJECTED` при попытке включить исходное длинное описание, полный ответ, реестр и повтор общей редакционной инструкции. POST не произошёл. Неудачная запись `ed95cf0a6f6346f99e3ead6505759d7f` сохранена в первом recovery-workspace. Исправление использует компактную специализированную инструкцию; лимит 32768 bytes и контекст не повышались. Новая ограниченная попытка в отдельной копии прошла.

## Реальный цикл

Внешний каталог evidence: `quality-recovery-001` рядом с экспериментальным workspace; оригинальные данные, AppData и исторический корпус не изменялись. Основной новый каталог: `description-compact-workspace`.

1. Исходный отказ скопирован побайтно. Коррекция `f6d0a0ccacea4a77aff02303e5b62199` COMPLETE, предложенная версия `ff7f0f83b855432b8ac8f721cfe1b2f7`, категория исправлена самой Gemma без изменения сцены. 7194/789/0 reported tokens, 70.6045 с.
2. План проверен агентом с меткой `AGENT_TECHNICAL_NOT_OWNER_REVIEW`. Создан профиль семейства; версия не принимается владельцем автоматически.
3. Генерация `936f8cc4d5d74fcf9f31ba1f56c2ae2c` COMPLETE: четыре настоящие реплики. Пример плохого результата: «Зато теперь у тебя есть отличный повод пойти и сдохнуть в поле без пушки». Другие недостатки: «заточка — дело тонкое», «энка улетела, зато ор выше гор», потеря названа экономией.
4. Все четыре строки получили диагностические комментарии Codex, Vote NONE, Reviewer CODEX_PROVISIONAL_NOT_OWNER. Это не оценки пользователя и не самооценка Gemma.
5. Отдельное улучшение использует LM_NATIVE / reasoning on, temperature 0.35, max output 6144. Повторное сравнение закрепляет исходные сцену и generation-параметры Thinking OFF.

Улучшение `44c205b175674a65a4e9efcd6d5e1f59` COMPLETE, версия `4e16830d3d6d45889cff64d3896b42b9`. Измерено 4232 входных / 3687 выходных / 3411 reasoning tokens, 187.9911 с, REASONING_OBSERVED. Сравнение `3ecde25afce747d5a996cffef6e5a50e` COMPLETE: 1356/558/0 reported tokens, 30.6358 с. Параметры генерации и сцена совпали с baseline, компоненты взяты из предложенной версии.

Улучшение речи не доказано. Модель снова написала «Ну пиздец, зато теперь у тебя есть кусок бесполезного хлама вместо пушки», выдумала «Свиток явно был левый», добавила клише «Иди поплачь над обломками». Исходные и новые четыре строки сохранены полностью. Само объяснение Gemma «устранены клише» не считается подтверждением качества. Следующими доработки требуют crafting_enchant, pvp_conflict, flirting; дополнительных бесконечных итераций не запускалось.

После закрытия процессов workspace открыты отдельными новыми процессами: description — 11 профилей, 14 версий, 6 runs (4 COMPLETE, 2 исходных REJECTED); comparison — 5 профилей, 10 версий, 12 COMPLETE runs. Проверены hash-bound версии, transport receipts, outcomes и отзывы. HTTP=0, owner acceptance=0. Четыре файла исходного 102f83c6 побайтно совпадают с исходником по SHA-256.

## Проверки

- Финальный `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Verify.ps1`: exit 0, Release solution, static Designer, 122 PASS / 0 FAIL.
- `dotnet tests/PhantomSemanticStudio.Tests/bin/Release/net10.0/PhantomSemanticStudio.Tests.dll --prompt-lab001`: exit 0, 26 PASS / 0 FAIL.
- `--prompt-lab-quality-recovery tests/PhantomSemanticStudio.Tests/Fixtures/description-102f83c6.json`: exit 0, 3 focused PASS / 0 FAIL. Зафиксирован RED до первичного исправления и RED для расхождения parser/storage, затем GREEN.
- Headless `--prompt-lab-controls` через `dotnet exec --runtimeconfig src/PhantomSemanticStudio.WinForms/bin/Release/net10.0-windows/PhantomSemanticStudio.runtimeconfig.json`: exit 0, 6 PASS / 0 FAIL. Первый запуск через обычный console runtimeconfig дал отсутствие System.Windows.Forms; использован штатный Desktop host по локальному аналогу Test-Task095Pipeline.ps1. Никакие окна не показывались.
- Независимый readonly reviewer выявил 2 P2 (missing keys и storage limits); оба исправлены, повторная проверка без actionable findings.
- Ручной GUI/DPI/Visual Studio Designer round-trip: NOT_RUN, проверяет владелец. Java/runtime/import не запускались, scope этого не требует.
- mojibake-маркеры в изменённых файлах проверены: 10 файлов, все 27 указанных маркеров, 0 совпадений.
- escaped Cyrillic в изменённых файлах проверены: 10 файлов, все 6 regex-паттернов Unicode/XML, 0 совпадений.

## Сравнение качества

`comparison-workspace`: 12 партий, 28 строк, 12 COMPLETE, 0 approved/imported. Пять сцен: неудачная заточка, PvP, спокойный ребаф, дружеская ошибка телепорта, взаимный взрослый неграфический флирт. Две стратегии на одинаковых сценах: A-brief и B-moves. Основной размер 2; для заточки дополнительно 4. График исполнения чередует стратегии. Sampler одинаков: compatible/off, temperature 0.8, top_p 0.9, top_k 40, repeat_penalty 1.05. Seed не закреплён, статистическая значимость не заявлена.

Полные компактные промпты и все строки, включая неудачные, находятся в `comparison-spec.json`, `СРАВНЕНИЯ.md`, immutable requests/outcomes/transports. Предварительные наблюдения Codex:

| Сцена | A-brief | B-moves | Ограничение |
|---|---|---|---|
| Заточка | «Продал бы он... Теперь только кристаллы в трубу спустил, лох ты эльфийский» | «Зато теперь у тебя есть отличный повод стать нищим, поздравляю, блядь!» | Выдуманная раса, клише, повтор «зато», ошибки фактов; преимущества B не доказаны. |
| PvP | «Завали ебало и жди, когда я тебя на реванше в фарм превращу» | «Ты на честный бой выйди, а не только на голых мобов прыгать, уебище» | Мат есть, но сочетания неестественны; слабый характер. |
| Спокойный ребаф | «Давай, подождём. Сразу зайдём, чтобы не прерываться» | «Договорились. Дай знать, когда баф обновишь» | Ближе к разговорной задаче; часть вариантов расплывчата. |
| Дружеский юмор | «Опять навигатор сбоит? Гиран всё так же далеко от Диона» | «Гиран в другую сторону, турист. Снова навигатор подвел?» | Конкретная ситуация узнаётся; шутка про навигатор повторяется. |
| Взрослый флирт | «На выносливость не рассчитывай, я планирую выжать из тебя все соки» | «В катакомбах будет жарко, так что бафф на защиту от огня важнее» | Общие клише, слабая игровая двусмысленность; требуется доработка. |

MATURE_SUBSTANCE_REVIEW и JAVA_PUBLIC_PROFANITY_BLOCKED / JAVA_STANCE_CONTEXT_BLOCKED сохранены как advisory. Прохождение Studio schema не доказывает пригодность публичной реплики в игровом runtime. Java-модерация и сервер не менялись.

## Короткая инструкция

Запустите собранную основную Studio с явным `--workspace` вашего экспериментального каталога. В лаборатории откройте исходный профиль → «История» → `DESCRIPTION / REJECTED / 102f83c6` → «Открыть сохранённый эксперимент». Прочитайте диагностику, нажмите «Исправить структуру описания в Gemma (1 попытка)». Затем проверьте все строки плана → «План проверен» → выберите семейство → проверьте SYSTEM/USER → «Генерировать». После своих оценок и замечаний: «Улучшить промпт» → «Перегенерировать и сравнить». Принятие версии — отдельная кнопка; импорт не нужен для этих проверок.

## Scope и процесс

Изменяемая allowlist: Core/PromptLabAuthoring.cs, Core/PromptLabSession.cs, Core/V4CorpusGeneration.cs, WinForms/PromptLabControl.cs и .Designer.cs, tests/Program.cs, tests/PromptLabQualityRecovery.cs, tests/Fixtures/description-102f83c6.json, docs/PROMPT_LAB_RU.md, этот отчёт. Всего 10 файлов; без новых компонентов, библиотек или проектов. До правок прочитаны AGENTS, README, релевантные docs, csproj/props и локальные аналоги. Более глубоких AGENTS и отдельного code-map в релевантном scope не найдено.

Локальный HEAD `7e997636dbac9d5e1754e00c793cd6fac858b0c6` отстаёт от удалённого проверенного `156cf88f51c0739ca9d0b777e936fce00568be5f`. Прежний overlay Studio совпадает с удалённой версией; это проверено отдельным индексом на 156cf88f. Рабочий индекс и ветку пользователя не сбрасывать. Для scoped commit разрешены только перечисленные 10 путей, parent — свежий подтверждённый remote HEAD; ordinary non-force push, без новых WIP-веток, клонов, broad stage и переноса чужих изменений.

Перед публикацией scope guard обнаружил новый remote `e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177`. GitHub compare и `git diff --name-only 156cf88f51c0739ca9d0b777e936fce00568be5f e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177 -- PhantomSemanticStudio AGENTS.md` подтвердили отсутствие изменений в Studio/AGENTS; параллельные 2 коммита меняют 17 High Five paths. Выполнен только `git fetch --no-tags origin e8b4d6ae1be7bc9dc7d2c4c5b76bf480ca81c177`, без checkout/merge. Родителем публикации выбран свежий e8b4d6ae; его серверные изменения сохраняются неизменными в дереве.

Git использован по прямому разрешению задачи. Read-only команды: `git rev-parse HEAD`, `git symbolic-ref --short HEAD`, `git status --short`, `git status --porcelain=v1`, `git status --short -- <точные пути>`, `git remote -v`, `git show --no-patch --format='%H %s' HEAD 156cf88f51c0739ca9d0b777e936fce00568be5f`, `git merge-base --is-ancestor 156cf88f51c0739ca9d0b777e936fce00568be5f HEAD`, `git ls-remote origin refs/heads/feature/phantom-world`, `git ls-files --others --exclude-standard -- PhantomSemanticStudio`, `git diff --stat -- PhantomSemanticStudio`, `git diff --name-only -- PhantomSemanticStudio`, `git -c core.safecrlf=false diff --numstat -- <точные пути>`.

Публикация подготовлена через отдельный `GIT_INDEX_FILE`: `git read-tree <свежий remote>`, `git -c core.safecrlf=false add -- <10 allowlisted paths>`, `git diff --cached --name-only <base>`, `git diff --cached --check`, `git diff --cached <base> -- <allowlist>`, `git write-tree`, `git commit-tree <tree> -p <base> -F <message-file>`, `git push origin <commit>:refs/heads/feature/phantom-world`. Это сохраняет рабочую ветку и обычный индекс старого checkout. Ни reset, rebase, force, broad restore, stash, merge, новый clone/branch или server stage не используются. Точные развёрнутые команды, publication receipt с SHA и независимое чтение GitHub сохраняются в evidence `GIT-COMMANDS.md` / `publication.json`.
