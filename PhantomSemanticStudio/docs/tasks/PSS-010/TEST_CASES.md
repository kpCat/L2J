# Исполняемые RED/GREEN — PSS-010

## T01–T03: пользовательский баг (обязательно initial RED)

- Fixture с `PATTERN привет → greet.reply`, first TEMPLATE `greet.01="Привет, {name}. ..."`, safe later `greet.02="Привет! Как ты сегодня?"`: полный `PackPreview.Reply` + `DialogueLabSession.PrepareTurn/CommitTurn` должен выдать `PACK_CATALOG_APPROXIMATE`, `greet.02`, существующий точный текст, не `FUNCTIONAL_OR_MEMORY_UNSUPPORTED`.
- Fixture `как дела → mood.share`, `mood.share.01` с `{interest}`, `mood.share.02="Неплохо, спасибо. А у тебя как?"`: реальный `mood.share.02`, без вымышленного интереса.
- `меня слили в пвп` без соответствующего PATTERN: `NO_PACK_MATCH`, EMPTY PACK, никакой генерации новых patterns, WorldHint GAME допустимо как advisory. Если при запуске против реального High Five находится иной PATTERN, отразить точный ID и отличие в evidence, без переписывания fixture.

## T04–T09: защита правды и стабильность

- Один лишь шаблон с `{name}`/`{interest}`/`{memory}` без предоставленных источников: `TEMPLATE_CONTEXT_UNAVAILABLE`/эквивалент и пустой PACK, а не заглушка с выдуманным значением.
- `PATTERN` functional/identity, `Fact`/`Recall` остаются `FUNCTIONAL_OR_MEMORY_UNSUPPORTED`, независимо от шаблонов.
- Безопасный шаблон без placeholders не обнуляется из-за других кандидатов с placeholders; отсутствие clean/mature/profanity-eligible ответов не маскируется как успешный PACK.
- Сценарий capture `{value}` если поддержан: только из пользовательского текста и в разрешённой bounded ветке; неподдержанный/unavailable capture блокируется.
- Повтор первого safe ответа и `Cancel`/`PrepareTurn` без `Commit` сохраняют корректный recent queue и IDs; никакого premature consumption.
- `UNKNOWN`/`FAMILIAR` band и `NEUTRAL`/`CASUAL` register, strict no mature/profanity, не подменять голос персонажа другим статусом.

## T10–T12: регрессии

- Старые PSS-007 A/B/C и STA controls (где доступны), `--pss-009-layout`, `scripts/Build-Verify.ps1` без FAIL.
- Actual source readonly probe: по согласованию ограниченное чтение двух v1 XML + PackReader; не писать в пользовательский High Five. Сохранить SHA/bytes до/после, никаких полных приватных данных в отчётах.
- Model off: `POST=0`, никаких зависимостей от LM, Java `NOT_RUN` если не запускалась, физический GUI/VS `NOT_TESTED` если недоступны. Возможный интерактивный ручной чек-лист: три исходные реплики, trace IDs, fallback и no fake values.

## RED/GREEN evidence

Сначала сделать падающие тесты с фактическим baseline, записать выход/exit. После изменения — теми же командами GREEN. Отдельный negative mutation: временно восстановить прежнюю `template.Text.Contains('{')` проверку или прежний первый-only selection, убедиться, что новые тесты снова красные; вернуть код в `finally` и повторить final GREEN. Не считать syntax/compile failure доказательством баг-теста.
