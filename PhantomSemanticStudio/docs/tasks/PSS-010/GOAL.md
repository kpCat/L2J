# PSS-010 — исправить ложные отказы лаборатории диалогов (LIVE-OBSERVED BUG)

## Основание и статус

Пользователь лично испытал приложение после PSS-009. В реальном импортированном High Five каталоге (65 файлов, 5 310 PATTERN, 20 963 TEMPLATE, fingerprint `43c49f49e298c7f35cfef8577f35a4dc50e585191d81073afc3f49ad3a81e322`) получил:

1. `привет` → `FUNCTIONAL_OR_MEMORY_UNSUPPORTED`; фактический PatternId `greeting.hello`, TemplateId `greet.01`, act `greet.reply`.
2. `как дела` → такой же ложный отказ; PatternId `mood.ask`, TemplateId `mood.share.01`, act `mood.share`.
3. `меня слили в пвп` → `NO_PACK_MATCH`, WorldHint UNKNOWN. Пока не доказано, что для этой конкретной фразы есть подходящий PATTERN: не превращать её насильно в положительный ответ.

**Это не означает, что 20 963 ответа непригодны.** Дефект в C# инспекторе: выбирает первый по ID TEMPLATE, уже умеет заменить плейсхолдеры поддельными `ТестовыйФантом`, `[интерес не задан]`, затем DialogueLabSession объявляет любой шаблон с `{}` unsupported и дополнительно требует равенства уже отрендеренного текста исходному шаблону. При этом реальные v1 `greet.02` и `mood.share.02` не требуют плейсхолдеров.

Это задача исправления **инспекции Studio**, НЕ изменения игрового Semantic Pack/Java. При полной Java parity заявлять не вправе.

## Git / работа

- Root: `C:\Users\ZBook\PhantomSemanticStudio\`.
- Repo: `https://github.com/kpCat/PhantomSemanticStudio.git`; expected `origin/main` = `9d5399e65c6c260ea2026c2da006cb48ba7bffce`.
- Сохрани текущую локальную ветку (исторически `master`), не создавай другую без явной необходимости; non-force push `origin HEAD:refs/heads/main` только в Studio.
- **Пользовательское изменение `PhantomSemanticStudio.sln` сохранять byte-for-byte, не включать в коммит.** Другой dirt тоже не терять.
- **L2J** `C:\Users\ZBook\L2J_Mobius\` целиком **STRICT READ_ONLY**. Никаких Git/Ant/Java/server/DB/temp/writes в нём. Только ограниченное чтение v1 humanized XML и документации.
- **No review ZIP**, no source ZIP, `scripts/New-ReviewBundle.ps1` не запускать. В Git не попадут чат-архивы/SQLite/личные сообщения/токены/модельные ответы.

## Порядок выполнения — ОДНА задача, HIGH

1. Read-first: `AGENTS.md`, `reports/PSS-009-final.md`, `reports/PSS-009-ui.md`, PSS-007 A review / тесты, все файлы данного PSS-010, relevant Studio Core/WinForms. Проверить worktree/remote/base и сохранность user dirt.
2. Read-only прочитать текущие `PackPreview.Reply`, `DialogueLabSession.PrepareTurn`, `PreviewResult`, `TextRules`, `DialogueLabForm.Send_Click`. Проверить XML именно тех v1 PATTERN/TEMPLATE, которые предъявил пользователь. Зафиксировать фактическую причинно-следственную цепочку и ограничения.
3. **RED сначала**: исполняемые тесты reproducing `привет`/`как дела` с templated-first + safe-literal-second, raw/placeholder mismatch, и третий legitimate no-match. Начальный RED должен честно воспроизводиться на фактическом baseline. Не подменять проверку grep/assert, делать public Core flow.
4. Исправить минимально и согласованно обоих владельцев логики — `PackPreview` и `DialogueLabSession`. Не добавлять GPT-generated filler, фиктивные имена, интересы, память или bogus Java parity. См. IMPLEMENTATION.md.
5. Добавить тесты на unsupported functional/Fact/Recall, known vs unavailable placeholders, повторение, отмену/очередь ответов, band/register, source drift и точные ID. Синтетические fixtures; опциональный actual source read-only diagnostic без изменения source. Проверки user phrases GREEN.
6. Release `scripts/Build-Verify.ps1`; targeted `--pss-010`; legacy A PSS007 и STA007, PSS009 layout, доступные общие проверки. Если live physical UI недоступен — `NOT_TESTED`, не приписывать PASS. Ни Java, ни live LM не нужны для этого Core-fix; соответствующие статусы отдельно.
7. Отчёты: `reports/PSS-010-plan.md`, `reports/PSS-010-final.md`, `reports/PSS-010-ui.md`, `reports/PSS-010-owned-files.txt`, только безопасные тестовые stdout; UTF-8/mojibake/escaped Cyrillic; exact inventory, reviewer findings и отрицательные проверки.
8. Только после PASS для разрешённых изменений — ordinary commit и **non-force push** в Studio `main`, проверить удалённый/публичный SHA. Даже при BLOCKED сохранить отчёт и безопасный checkpoint; не заявлять полного GREEN без реальных тестов. **STOP после PSS-010**.

## Успех задачи

Три пользовательские реплики проверяются так: `привет` и `как дела` возвращают настоящий пригодный буквальный ответ из **того же существующего каталога** (точные TemplateId/PatternId), а `меня слили в пвп` может остаться `NO_PACK_MATCH`, при этом контекстное GAME-предположение допустимо и помечено только advisory. Ни один ответ модели, фейковая подстановка, игровое действие или запись в High Five не допускается. Ложный `FUNCTIONAL_OR_MEMORY_UNSUPPORTED` на безопасной обычной фразе устранён, настоящие функциональные/memory ветви не выдаются за поддержанные.
