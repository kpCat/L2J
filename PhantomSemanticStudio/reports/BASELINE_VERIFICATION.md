# Проверка исходной поставки Phantom Semantic Studio 0.1

**Статус: SOURCE_STARTER / REQUIRES_WINDOWS_BUILD. Не GREEN-релиз приложения.**

## Выполнено в текущей среде
- Созданы solution с тремя проектами, исходники ядра, стандартная WinForms-форма, 6 designer tabs, .resx, скрипты и документация.
- Статическая проверка структуры: 10/10 PASS (`static_checks.py`, приложен в scripts как дополнительный необязательный Python verifier).
- Перекрёстная проверка: все 24 обработчика из Designer присутствуют в MainForm.cs; ссылки на controls найдены.
- XML проектов и resx разбираются; файлы сохранены UTF-8.
- Проверено отсутствие циклов, if/switch, LINQ, lambdas, I/O и async в InitializeComponent. Это структурная проверка, не запуск дизайнера.
- Подготовлены 36 C# console test cases. **Они не выполнялись.** Их количество не является количеством PASS.
- Код reader не содержит операции записи; экспорт пишет только в собственный workspace. Проверки этой архитектуры runtime ещё должен выполнить Codex.

## Не выполнено
- `dotnet build PhantomSemanticStudio.sln -c Release --nologo`: не запущена компиляция, shell вернул `dotnet: command not found`.
- .NET SDK отсутствует. Попытка загрузить SDK не удалась из-за недоступности сетевой загрузки/DNS; сторонние binaries в архив не включались.
- Windows/Visual Studio Designer и DPI UI: NOT_RUN.
- Все C# runtime tests: NOT_RUN.
- Импорт реального локального L2J с Windows-пути: NOT_RUN; доступен read-only просмотр GitHub-контракта.
- Реальная Gemma/LM Studio: NOT_RUN.
- Java validator / Java parity / game server / DB: NOT_RUN и не запускались по границе задачи.
- Независимое ревью другим исполнителем: NOT_RUN. Выполнено только авторское чтение/статические проверки.

## Исправления при авторском чтении
- Exporter теперь явно отклоняет stale approved-кандидата вместо молчаливого пропуска.
- На диск сохраняется metadata source snapshot, а не многомегабайтная дублирующая копия всех записей.
- HTTP-тесты используют абсолютный временный путь своей ОС.
- Исправлено PowerShell-приведение списка trim-символов в builder review ZIP.

Нельзя превращать этот отчёт в заявление «сборка/все тесты/Designer пройдены». PSS-001 начинается именно с реальных проверок и исправлений на Windows.
