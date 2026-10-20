# PSS-006 — известный формат источника и privacy-политика

## Проверенное на предоставленном пользователем примере chat.zip

- 68 file entries, суммарно 67 025 209 uncompressed bytes, 15 851 829 compressed bytes. Встречаются `chat.DD-MM-YYYY.log` и `chat.log.MM-DD-YYYY-1`; **имя файла не является авторитетом времени**.
- Основная строка: `[27.12.21 00:00:19] SHOUT [PlayerA] wts ...`.
- Приватная строка: `[27.12.21 00:00:20] TELL [PlayerA -> PlayerB] text ...`.
- Реальные имена и реплики здесь **заменены синтетическими плейсхолдерами**. Не копировать никакую строку из чужого реального лога в задачу, публичные tests или reports.
- Utf-8 наблюдался на samples, но не считать каждый archive целиком гарантированно UTF8. Различать valid UTF8, UTF8 with BOM, errors и ambiguous encoding. Не молча заменять повреждённые байты/символы; запись skipped с aggregate count.
- Приоритет: дата/время из самой строки; неопознанные строки считать отдельно; сохранять timezone `unspecified` (не объявлять UTC). Поддержка строк с вариациями spacing, CRLF/LF и именами из нестандартных символов нужна через точные синтетические fixtures.

## Жёсткие security limits (разумный старт, обсуждаемые значения документировать)

- Zip read-only; никакого general extraction, никаких symlink/junction/reparse entries, абсолютных путей, `..`, NUL, вложенных архивов, исполняемого контента, password/encrypted entries.
- Max entries 500, max archive compressed 256 MiB, max total uncompressed 256 MiB, max single entry 64 MiB, max total lines 3 000 000, max line bytes 16 KiB, max expansion ratio 100:1. Бюджеты проверять до и во время stream, не полагаться только на ZipInfo length. Если файл .log больше 64 MiB, отдельный понятный BLOCKED_RESOURCE с предложением разделения, а не безусловный подъём лимита.
- Max visible text length per record 4000 Unicode chars. Control characters/invalid sequence → reject/sanitize for UI, исходная raw строка не должна попадать в публичную телеметрию.
- Import sink только LocalAppData protected workspace/corpora, transaction/.partial и контрольная сумма входного архива. Никаких имён/nicks в названиях файлов.
- `TELL`, `FRIENDTELL` и aliases — skip **до локального DB insert**, кроме агрегированных counts. Это PRIVACY_BY_DEFAULT, даже если пользователь пытается фильтровать. Не добавлять opt-in private в PSS-006.
- Для остальных каналов скрывать user identifiers по умолчанию; если хранится псевдоним для построения последовательностей, использовать per-corpus salted pseudonym, salt private in workspace, не публиковать. Contact/URL/PII heuristics — best-effort и отдельный флаг; не выдавать очищенный корпус за анонимный гарантированно.

## Triage языка и транслита

`CYRILLIC`, `LATIN_TRANSLIT_CANDIDATE`, `EN_OR_OTHER`, `MIXED`, `UNKNOWN`. Здесь нет автоматического массового перевода: `privet kak dela` может быть русским транслитом, `need pt` — игровым английским, `den exoume` — другой язык. Нет доказанной language identification по нескольким символам. Строки с высокой неопределённостью помечать UNKNOWN, не переписывать оригинал.

## Synthetic test fixtures

```text
[01.01.22 00:00:01] SHOUT [PlayerA] privet, kto na spot?
[01.01.22 00:00:02] PARTY [PlayerB] пошли на фарм
[01.01.22 00:00:03] FRIENDTELL [PlayerA -> PlayerB] SECRET_FIXTURE_NOT_TO_PERSIST
[01.01.22 00:00:04] TELL [PlayerB -> PlayerA] SECRET_FIXTURE_NOT_TO_PERSIST
[01.01.22 00:00:05] SHOUT [PlayerC] need party
```

Проверить, что `SECRET_FIXTURE_NOT_TO_PERSIST` отсутствует не только в UI, но и в физических DB/page/index bytes. Проверить, что `PlayerA`/`PlayerB` не оказываются в публичных reports. Огромный synthetic fixture создавать программно вне Git и без настоящих чатов.
