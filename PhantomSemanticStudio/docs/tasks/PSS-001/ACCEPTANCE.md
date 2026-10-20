# Acceptance PSS-001

## Обязательные доказательства
- `dotnet build PhantomSemanticStudio.sln -c Release --nologo`: реальный exit 0.
- `dotnet run --project tests/PhantomSemanticStudio.Tests -c Release --no-build`: реальный итог без FAIL. Проверить, что intentional negative controls действительно падают на нарушении, а не ловят собственную Assertion exception.
- `scripts/Verify-Designer.ps1`: PASS, но это отдельно от VS Designer.
- Designer: MainForm открыта, вкладки видны, сохранение и повторное открытие не ломают конструктор. Отсутствующий UI доступ не приравнивать к PASS.
- Runtime UI smoke: настройки/импорт/библиотека/кандидаты/замечания/export, 100% и 150% DPI, нет наложений. Не запрашивать полноценный ручной вход в L2-клиент.
- Actual import: количество/файлы/fingerprint из локального пака, только чтение. Все source SHA до/после одинаковы.
- Реальный LM Studio: один запрос 4–6 кандидатов либо BLOCKED_LM с точной причиной. Fake HTTP не доказательство реального endpoint.
- Выбор и повторное применение сохранённого замечания не изменяет source или все темы автоматически.
- Экспорт только review ZIP за пределами L2J. `NOT_SERVER_VALIDATED` внутри и в UI. Никакого установочного сценария.

## Негативные случаи
1. Workspace внутри source, source внутри workspace, junction/symlink, ../ и абсолютный путь из manifest.
2. XML DTD/XXE, неверный UTF-8, слишком большой файл, неполная v2-пара, отсутствующий сегмент, duplicate/invalid override.
3. Повтор фразы при смене регистра/пунктуации/ё; конфликт одинакового pattern с другим act; повторы в новой партии.
4. Незнакомый topic/act, functional/support/identity act, неизвестные placeholders, гендерное ограничение без runtime support.
5. Редактирование одобренной записи, смена source fingerprint, повторный экспорт при source drift; никакого молчаливого skipping/rebase.
6. Malformed/empty/fenced/extra-key/duplicate-key JSON, незавершённый ответ по токенам, tools вместо content, HTTP 4xx/5xx, превышение response limit, cancel/timeout.
7. Два экземпляра workspace, прерванное сохранение, ошибки доступа: сохранить старую корректную state, не терять принятую историю.
8. Несохранённое редактирование при смене строки/вкладки/закрытии: не терять тихо; дать Save/Discard/Cancel где нужно.
9. Ненормативная лексика или условия из текста не получают автоматически label NONE/универсальность при XML-export.

## Статусы
Implementation: GREEN только если все обязательные локально выполнимые gates реально прошли; иначе BLOCKED/FAILED с перечислением.
Designer/LM/Java — отдельные точные статусы. Java здесь ожидаемо NOT_RUN; это не блокер review-only версии, но блокер любого server-ready export.
Push: PUSH_OK либо PUSH_NOT_CONFIGURED. Нельзя «исправить» отсутствие remote пушем в kpCat/L2J.
Независимое внешнее ревью — PENDING_INDEPENDENT_REVIEW, не самооценка ACCEPT.
