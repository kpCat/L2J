# Phantom Semantic Studio — план реализации

**Цель:** поставляемый стартовый исходный проект и отдельный этап доработки Codex.
**Архитектура:** read-only reader → immutable snapshot → кандидаты в собственном workspace → review-only ZIP.
**Стек:** .NET 10 / WinForms / встроенные библиотеки.
**Спецификация:** docs/DESIGN_RU.md.

## Общие ограничения
Не изменять L2J, не запускать сервер/БД/Ant/Git из приложения. Нет сервисов/IO в конструкторе формы. JSON от модели недоверенный. Нельзя обещать Java-parity без доказательства.

## Фокус ревью
Путь внутри L2J, junction/symlink, конфликт source/workspace; изменение уже одобренного текста; malformed/truncated JSON; XML с DTD; импорт custom override без потери исходной provenance.

## Шаги
1. Создать контракты и C# проверки; отдельно проверить статические ограничения файлов и Designer.
2. Реализовать bounded reader, normalizer, candidate validator, isolated workspace/export.
3. Реализовать LM Studio-клиент с JSON Schema, таймаутом и отменой; тестировать fake HTTP.
4. Реализовать обычную форму из шести вкладок с ручным одобрением и экспортом только для ревью.
5. Проверить артефакты, записать реально выполненные и невыполненные проверки; составить точный handoff Codex.
