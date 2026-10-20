# PSS-007 — большая лаборатория диалогов с Semantic Pack + Gemma-наставник + корпус чатов

## Кто и где
- Это **одна крупная задача A/B/C**, с внутренними самостоятельными test/commit checkpoints, не три отдельных диалога. Один новый Codex conversation.
- Рабочий root `C:\Users\ZBook\PhantomSemanticStudio\`, репозиторий **только** `https://github.com/kpCat/PhantomSemanticStudio.git`, push `HEAD:refs/heads/main`.
- **Required base** `origin/main = 8a583e5bc872d3f61b933f217baa985af5425e5f`. Проверить exact local HEAD, remote SHA, origin URLs и dirty inventory до изменений. При drift — BLOCKED_REMOTE, без force/reset/rebase.
- Codex: основная coding model; reasoning **HIGH**. Без повторного согласования на каждом подэтапе; пользователь уже задал программу и режим безопасной работы. Не начинать PSS-008.

## Цель пользователя
Пользователь хочет **поговорить со своим разговорным Semantic Pack High Five**, а не с нейросетью, переодетой фантомом. Роль Gemma — *отдельный редактор/наставник*, который по желанию пользователя выявляет неоднозначность («босс» — рейд или начальник), уточняет один вопрос, предлагает scope и то, что потенциально стоит запомнить. Никакого скрытого повышения прав, автозамены reply, «обучения весов» или установки XML.

Пример UX: «Меня этот босс достал» → если контекст неоднозначен, рядом (не от имени фантома) Gemma спрашивает «О рейд-боссе или начальнике?» → ответ «о начальнике» уточняет ТОЛЬКО локальный контекст; ответ фантома по-прежнему возможен лишь из каталога, иначе честное `NO_PACK_MATCH`. Сохранить предложенное правило можно только отдельным явным действием редактора.

## Исходный статус PSS-006
- Commit выше опубликован: GitHub report `reports/PSS-006-final.md`, `98 PASS / 0 FAIL`, `0 warnings / 0 errors` по логам; это не доказательство нового PSS-007.
- Local chat.zip: 68 log entries, 685498 публичных строк в собственном SQLite, 336473 приватных пропущены до записи; corpus в `workspace/corpora`, исходный ZIP пользователь хранит в `artifacts/private-input/chat.zip`. **Не отправлять оригинал/сырые строки в GitHub или LM автоматически.**
- `PackPreview` — **приблизительный C# inspector**, НЕ Java runtime parity. Операторский PSS-003 Java content gate — отдельный, GUI Java/Ant/shell не запускает. `LIVE_LM` не подтверждён; PSS-006 model unloaded, никакой auto-load/unload.
- Physical GUI/DPI100/150/VS Designer round-trip **NOT_TESTED** и остаётся отдельным manual gate.

## Подэтап A — честный разговор с паком
Реализовать отдельную «Лабораторию диалогов» в обычном Designer-authored WinForms окне, открываемом из существующей вкладки «Диалог и обучение». Диалоговое состояние и trace через Core; библиотека и PackSnapshot только на чтение; несколько реплик, история с ограничениями, mode AUTO/GAME/REAL/MIXED, явные relationship/register. **Любой reply фантома только результат каталога с real PatternId/TemplateId**. Недостаток совпадения → `NO_PACK_MATCH`, никакой fallback текста от Gemma. Маркировать `PACK_CATALOG_APPROXIMATE / NOT_JAVA_RUNTIME_PARITY` и показать act/topic/source fingerprint; не приписывать Java selector, identity/social/память/functional-first. Контекст GAME/REAL — редакционная гипотеза, Java пока не умеет её runtime-gating.

## Подэтап B — отдельный Gemma-наставник и управляемая память
Только по явному session opt-in включать `Gemma помогает` (default OFF), один запрос для действительно неясного turn или по кнопке «Спросить Gemma»; не опрашивать все фразы, не делать retry/JIT lifecycle. Нужен строгий structured output / bounded prompt, честные `BAD_RESPONSE/BLOCKED_LM`, вопросы только в отдельной роли `MENTOR`, а не `PACK`. Максимум 1 model question на turn, один pending clarification и отдельное явное подтверждение интерпретации. Текущая память беседы — эфемерная; сохранение долгосрочного редакторского замечания/правила — отдельный click с явным topic/act/source review, без silent automatic Candidate/APPROVED. Бounded local private session save только по выбору пользователя.

## Подэтап C — сырьё корпуса → выбранные примеры / translit / перевод в мастерскую
Разрешить вручную выбирать в `ChatCorpusForm` **1–20 public** фрагментов и явно отправлять их в лабораторию/редакторский черновик. Перед передачей показать полный sanitized preview, предупреждение о best-effort PII; никакой автоматической отправки в Gemma. Латиница ≠ транслит: показывать 3 категории `возможный транслит/иностранный/непонятно`, разрешить manual override. По отдельной кнопке в лаборатории — bounded **1–3** выбранных фрагмента для запроса Gemma «предложи русскую транслитерацию ИЛИ перевод, либо UNKNOWN», строго schema. Исходная строка immutable; предложение AI остаётся черновиком/заметкой, не записывается обратно в корпус/пак. По явному выбору пользователя преобразовать нужную редакционную заметку в DRAFT или scoped lesson только в существующей безопасной conversation topic/act связке; без approval и XML.

## Общий бюджет и STOP
- Не вводить новый backend/provider, game DB/Java/runtime, mass 700k LM inference, scraping/network service, automatic corpus reindex, code execution by model. Не менять `L2J_Mobius` даже во временные файлы.
- 3 внутренние последовательные части A/B/C: RED→GREEN каждой, focused targeted tests, затем один финальный `scripts/Build-Verify.ps1` + static Designer + отдельные STA control tests. В конце commit(s) и non-force push **только Studio**. Блокировка LM/UI не препятствует независимым offline тестам; писать BLOCKED/NOT_TESTED честно.
- Результат: `reports/PSS-007-plan.md`, `reports/PSS-007-final.md`, `reports/PSS-007-ui.md`, `reports/PSS-007-owned-files.txt` и необходимые обезличенные evidence (без raw chats). **Никаких review/source ZIP от Codex.**
- STOP после PSS-007. Следующий PSS-008 — coverage/v3 isolated release, отдельно.
