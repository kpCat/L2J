# PSS-001 — довести starter до проверенной read-only версии для Windows

## Исполнение
Рабочий каталог: `C:\Users\ZBook\PhantomSemanticStudio\`.
Модель: основная coding-модель Codex, не Spark/мини; **уровень мышления Высокий (High)**.
Новый диалог; выполнить один самостоятельный checkpoint без повторного согласования. Не начинать PSS-002.

## Что пользователь хочет
Отдельную WinForms-программу, которая через локальную Gemma помогает обогащать Semantic Pack: задавать темы, пол, характер, слова и нюансы; проверять ответы по паку; объяснять ошибки; получать новые кандидаты; вручную принимать только нужное. Модель не пишет код. Нельзя бесконтрольно редактировать действующий пак. WinForms должен нормально открываться в Visual Studio Designer; InitializeComponent без циклов/фабрик/IO.

## Baseline
Поставка — ZIP исходников, а не опубликованный C# Git commit.
`BASELINE_MANIFEST.json` SHA-256: **191087dce21f3613feb892525ba9ad1604619d70e791e2a534fe15aff735faaa**.
Проверить перечисленные SHA до правок, расхождения записать как user changes, не затирать. Манифест исходной поставки не переписывать задним числом.
Required parent: при существующем самостоятельном Git root — фактический initial HEAD, записанный в отчёте; при новом каталоге — `NO_GIT_BASELINE / source manifest`.
Не выдумывать required SHA нового проекта и не использовать SHA L2J как C# parent.
Reference для чтения L2J: `feature/phantom-world`, `3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25`. Локальный L2J может быть новее — не checkout/reset/fetch/merge; сверить только нужный контракт и зафиксировать отличие.

## Абсолютный запрет записи
`C:\Users\ZBook\L2J_Mobius\` — **READ_ONLY** целиком.
High Five: `C:\Users\ZBook\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\`.
Нельзя менять Java/XML/manifest/custom/INI/SQL/БД/геодату, запускать/останавливать сервер или клиент, выполнять Ant/JAR, создавать там build/obj/log/tmp, коммитить/пушить в L2J. Не трогать `.phantom-local` и текущую другую разработку пользователя.
Не добавлять в программу возможность записи в L2J, автопубликации, auto-approve, исполнение кода модели, обходы валидации или автоувеличение лимитов.

## Read first
1. `AGENTS.md`, `README_RU.md`, `docs/DESIGN_RU.md`, `docs/SOURCE_AUDIT_RU.md`.
2. `reports/BASELINE_VERIFICATION.md`: C# build/runtime/Designer НЕ ПРОХОДИЛИ. Не доверять исходной поставке как GREEN.
3. `docs/tasks/PSS-001/ACCEPTANCE.md`, `IMPLEMENTATION.md`, `SAFETY.md`, `HANDOFF.md`.
4. Короткая инвентаризация src/tests/scripts, доступных SDK, собственной Git-границы. Не читать весь L2J.

## Результат этой задачи
Собрать и исправить WinForms starter, реально проверить импорт пользовательского humanized v1/v2/v3/custom, генерацию кандидатов и ручное ревью, усилить отрицательные тесты. Добавить удобное повторное использование сохранённых замечаний в диалоге. Экспорт остаётся **REVIEW_ONLY / NOT_SERVER_VALIDATED**.

Существующий пак не улучшается этой задачей сам по себе: создаётся инструмент и проверенные кандидаты. Публикация реального Semantic Pack и Java parity — отдельная следующая задача.

## Бюджет
Не делать broad L2J audits/full verify, stress/overnight tests, массовую генерацию 20k фраз и переустановку SDK/VS без необходимости. Максимум 3 live-запроса к LM Studio; после конкретной ошибки не повторять автоматически. Один финальный aggregate Build-Verify после focused checks; rerun только упавшего gate после исправления. При недоступной LM Studio/Designer продолжить остальные независимые пункты и честно зафиксировать BLOCKED/NOT_TESTED, не симулировать успех.

## Завершение
Реальные проверки → report → review ZIP → обычный commit в собственном репозитории → обычный push только в заранее настроенный origin этого приложения, НЕ kpCat/L2J → SHA/статусы → STOP. Если origin отсутствует: PUSH_NOT_CONFIGURED, review ZIP обязателен. Не создавать удалённый репозиторий и не придумывать remote.
