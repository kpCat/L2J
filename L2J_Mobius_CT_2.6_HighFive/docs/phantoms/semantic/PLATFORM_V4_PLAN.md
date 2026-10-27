# TASK095 — план и внутренний обменный формат

Spec: `../tasks/PHANTOM-SEMANTIC-PLATFORM-095/TASK.md` и его контракты. Baseline: `c212b49b2634a109f9d5c50486d9fb02fb94e07e`.

Архитектура: отдельный строгий authoring/compiler в существующем C# Core, offline CLI и WinForms; additive indexed backend у настоящего Java conversation owner. Старые данные v1–v3 и WIP094 сохраняются. Новых зависимостей, DB, GameServer, native-world проверок нет. TASK095 уже задаёт утверждённый дизайн и разрешает автономное исполнение; промежуточного approval не добавляем.

Bounded exception: больше 10 файлов необходимо для одного сквозного результата. Allowlist families: новые V4-файлы Core/CLI, V4 форма и её Designer, точечный MainForm entrypoint, solution/project registration; новые Java humanized v4 backend/tests и причинные conversation/config/composition call sites; scripts content proof; semantic v4 registry/demo и четыре пользовательских документа; отчёт TASK095. Не менять Party/combat/navigation, старые task packages, рабочие настройки и v1–v3 pack bytes.

## Внутренний compiled contract `PWV4/1`

Immutable stage содержит `manifest.tsv`, `registry.tsv`, `voices.tsv`, `events.tsv`, `triggers.idx`, `replies.idx`, `records.bin`. Всё, кроме бинарных index, strict UTF-8 без BOM, LF. Manifest: первая строка `PWV4\t1`, затем отсортированные строки `file\tNAME\tBYTES\tSHA256`. Ровно шесть объявленных payload-файлов (семь вместе с manifest), SHA lowercase hex. Manifest hash — SHA256 его bytes. Caps: <=1,000,000 records, <=2 GiB records.bin, <=48 MiB каждый index, <=4 MiB registry/voices/events, <=16 KiB одна runtime запись. Без дополнительных файлов/ссылок/изменения после проверки.

`records.bin`: UTF-8 TSV строки LF; поля: id,mode,category,act,text,intent,ambiguity,stance,tone,relations,voices,facets,profanity,maturity,tags,weight,requiredEvent. Массивы comma-separated, facets `key=value` sorted; TAB/CR/LF внутри поля запрещены. Порядок records — ordinal ID; ID — детерминированный SHA256 канонических данных с namespace. Все runtime ссылки сверяются с реестром.

Оба index состоят из 48-byte records: digest SHA256 (32 bytes), records.bin byte offset (big-endian int64), byte length без LF (big-endian int32), weight (big-endian int32). Сортировка unsigned digest, затем ID. Trigger digest = SHA256 normalized text UTF-8; reply digest = SHA256 `mode\tcategory\tstance\trequiredEvent`. Нормализация: NFKC, lowercase invariant/Locale.ROOT, collapse whitespace, trim. Binary search определяет диапазон; подбор проверяет ограниченное число кандидатов, не сканирует корпус. Индексы доступны через read-only mapped/file access; тексты не загружаются целиком в Java heap.

После ревью уточнён общий cross-file контракт: каждая исходящая комбинация mode/category/stance/event/relation должна иметь безопасный ANY/no-facets/NONE/GENERAL fallback со спокойным tone. Не более 32768 context buckets. Java сохраняет только до девяти fallback offsets на bucket, дополняя основной ограниченный подбор; исчерпание недавних безопасных вариантов допускает молчание. TRIGGER содержит только буквальный текст. Исходящий текст — до 100 Unicode codepoints и 240 UTF-8 bytes, как у существующего execution writer; для `{name}` резервируются 64 codepoints / 192 bytes. Пустой runtime stage отклоняется.

registry.tsv: category,label,act,intent (четыре TSV поля). voices.tsv: key,label,legacy,profanityTolerance,insultTolerance,sexualHumorTolerance,conflictStyle,trustThreshold,facets (девять полей). Dimensions 0..100, facets — десять существующих ключей и значения 0..100. events.tsv: code,label. Реестр authoring — данные Core; exporter паспорта читает те же versioned объекты, что валидатор.

## Исполнение и проверки

- [x] Core: RED→GREEN strict JSONL/negatives; immutable atomic imports, normalized dedup, registry proposals с явным approval, deterministic build/verify, CLI.
- [x] Java: RED→GREEN production backend + реальный Humanized owner, functional-first/default OFF/v3 parity, persona/boundaries/events, bounded selection.
- [x] WinForms: отдельный offline import/review/accept/build/preview/categories/passport поток, layout в Designer, I/O вне constructor.
- [x] Corpus/docs: 24+ voices, starter leaves, три режима, 200 distinct fixture; единый сгенерированный паспорт и русская инструкция.
- [x] Final technical gates: C# Release/legacy/GUI controls, isolated Java production gate, deterministic 200/10k/100k scale, exact scope/encoding/WIP verification, независимое ревью. Точечный commit и normal push выполняются после этих gates; их SHA/exit фиксируются отдельным publication receipt.

Review focus: malformed/duplicate JSON и Unicode; corrupt index/reference/hash; cross-file rollback и dedup; mature/consent/functional priority; immutable stage и reparse/source-write guards. OWNER_CHAT_QA_PENDING и REAL_GAME_QA_PENDING явно отделяются от технической приёмки.
