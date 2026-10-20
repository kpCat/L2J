# Техническая диагностика (проверено на опубликованном коде)

## Где дефект

- `src/PhantomSemanticStudio.Core/PackPreview.cs`, метод `Reply`: после нахождения обычного PATTERN выбирает первый eligible TEMPLATE по `Id`; затем делает `Replace("{name}", "ТестовыйФантом")`, `Replace("{memory}", "[память не подключена]")`, `Replace("{interest}", "[интерес не задан]")`. Это подстановка вымышленных сведений, а не содержание честного каталога.
- `src/PhantomSemanticStudio.Core/DialogueLab.cs`, `PrepareTurn`: `template.Text.Contains('{') || template.Text.Contains('}')` маркирует любой шаблон с placeholders как `FUNCTIONAL_OR_MEMORY_UNSUPPORTED`, включая `{name}` и `{interest}`. Дополнительно `reply.Text == template.Text` невозможно после честного рендера части шаблонов. Результат намеренно обнуляется.
- Тест `tests/PhantomSemanticStudio.Tests/Pss007.cs` использует `greet.base` БЕЗ placeholders; реальный v1 path не покрыт этим тестом. Именно поэтому все предыдущие GREEN не нашли пользовательский баг.
- `WorldScope.InferWorld` в `DialogueLab.cs` распознаёт латиницу `pvp`, но не кириллическое `пвп`, вследствие чего `меня слили в пвп` остаётся `UNKNOWN`. WorldHint не является игровой семантикой и не должен исправлять `NO_PACK_MATCH`.

## Фактические v1 XML (High Five, read-only)

`dist/game/data/phantoms/semantic/humanized/high-five-ru-humanized-semantic-v1.xml`:

- `greeting.hello`: `phrase="привет"`, `act="greet.reply"`; без `fact`/`recall`.
- `mood.ask`: `phrase="как дела"`, `act="mood.share"`; без `fact`/`recall`.

`dist/game/data/phantoms/conversation/humanized/high-five-ru-humanized-conversation-v1.xml`:

- `greet.01` содержит `{name}`: без подтверждённого runtime identity нельзя его тихо превращать в ответ с вымышленным именем.
- `greet.02`: `Привет! Как ты сегодня?` — пригодный альтернативный обычный шаблон.
- `mood.share.01` содержит `{interest}`: без реального persona interest нет доказанного значения.
- `mood.share.02`: `Неплохо, спасибо. А у тебя как?` — пригодный альтернативный обычный шаблон.

## Почему НЕ надо «фиксить» High Five

Java `PhantomHumanizedCatalog.select(...)` получает owner name, value, memory, interest и умеет рендерить placeholders при реальном runtime. C# не является полной реализацией такого runtime и не имеет права выдумывать их. Исправление — безопасный отбор существующих TEMPLATE по известности контекста и честные статусы. Это не замена Java selector, social/persona/identity, profanity/mature gates.

Изучить сначала причины и проверить RED; не принимать этот документ за лицензию на архитектурный refactor.
