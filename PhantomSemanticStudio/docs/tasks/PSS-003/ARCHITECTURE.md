# PSS-003 — целевая архитектура, границы владельцев

```text
Approved Candidate(s) + текущий PackSnapshot + явная редакционная аттестация
     ↓ [Validate exact approval+source+all peers, fail entire batch]
Preflight -> StagePlan (только память, уникальные стабильные ID)
     ↓ [recheck source, path guard, copy/hash from local High Five READ_ONLY]
workspace/proposals/<id>/module/dist/game/data/phantoms/
  ├─ untouched: v1, v2, v3, manifest, TSV, остальные custom
  ├─ staged change: semantic/custom/my-social-topics.xml
  ├─ staged change: conversation/custom/my-phrases.xml
  └─ stage receipt: separate adjacent JSON with hashes, no raw user content
     ↓ [operator separately chooses to validate]
workspace/proposals/<id>/module/{java,test,dist/libs,build.xml}  <- copies only
     ↓ [Ant/JDK25: only shadow root, DB-free target]
CONTENT_JAVA_VALIDATED / BLOCKED_JAVA / FAILED_JAVA
```

## Trust boundaries

- `PackReader` источник только для инспекции, **не** полная Java-parity. `CandidateReview.IsCurrent` + `CandidateValidator` обязательны при каждом staged export, но сами по себе не дают `SERVER_READY`.
- `XML` — исключительно **STAGED PROPOSAL**, ни одно действие не пишет в L2J. Доступ к данным кандидатов остаётся в собственном workspace, не в репозитории. Никакого распаковывания поверх `dist`.
- Candidate GUID становится deterministic `pss.p.<32 lower-case hex>` или `pss.t.<32 lower-case hex>` (подтвердить Java `KEY` regex). Existing `id`/normalized text clashes — отказ; `override="false"` всегда; зависимые от identity/functional/gameplay act не поддерживаются.
- `PATTERN`: `phrase` берётся из candidate.Text и идёт через Java-compatible bounds/normalization, `salience="0"`, `ttlMinutes="0"`, `priority="500"`, без `fact`/`recall`; `TEMPLATE`: `band`, `register` из candidate, provisional `profanity="NONE"` **только после явного редакционного подтверждения**; `mature` не выставляется.
- Все существующие нецелевые файлы сохраняются byte-for-byte. Target custom XML может сериализоваться заново *только в stage*, с сохранением всех существующих элементов, атрибутов, overrides и UTF-8 кириллицы. Финальная версия не меняет L2J ни до, ни после Java validation.
- `PathSafety` / `WorkspaceStore` проверяют disjoint, reparse/junction, relative path traversal; нет Git/Java/Ant/Process.Start в исполняемой GUI-программе. Java выполняется отдельно операторским скриптом, в scratch.
- Staging receipt: source fingerprint, точный список source SHA+bytes, candidate IDs / approval hashes / text SHA, target file hashes, Java command+exit+status, timestamp; без raw generated text, prompt, token, credentials и больших stdout в Git.
- Обновления STATUS не отменяют ранее сохранённое approval. Изменение candidate или baseline блокирует новый staging; старый staged каталог не приобретает статус актуального после изменения source. Не надо менять статус кандидатам на `EXPORTED` без доказанной обратной совместимости.

## Java oracle

Точный baseline Java: `kpCat/L2J` → `feature/phantom-world`, reference SHA `3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25`; локальный High Five может опережать этот SHA. **Фактический локальный источник** необходимо прочитать и зафиксировать SHA, без checkout/update. `PhantomHumanizedCatalog.loadV3` читает v1/v2/v3/manifest, затем custom; `ant phantom-humanized-v3-content-validate` — узкий DB-free gate.

Важно: `build.xml` определяет `build=../build` и `compile-tests` удаляет build/test dirs. Поэтому **запускать Ant на оригинальном пути запрещено без исключений**. Работать только с копией `build.xml`/src/libs/test и физической staging-копией данных под root Studio, где все выходные пути физически находятся внутри scratch. Перед запуском проверять resolved basedir/build/output; нет прав писать в L2J. Нельзя запускать `ant verify`, `jar`, тесты с БД, внешние службы.

Если target не гарантирует фактическую проверку custom именно в заданном staged root, добавить узкий Java bridge/fixture **в shadow**, с вызовом `loadV3(root,true)` и сверкой counters/expected values. Не менять Java-файлы L2J; не утверждать, что проход base-only теста доказывает custom. Java status: `PASS_JAVA_STAGED` только с доказанным parser/load result, иначе `BLOCKED_JAVA`/`FAILED_JAVA`. Даже PASS_JAVA_STAGED **не** равен runtime behavior, gender safety, production publish или live client.

## UI

Добавлять контролы только явным кодом в `MainForm.Designer.cs`, обычными WinForms controls, с полноценным дизайнерским layout/anchor/tab order. `MainForm()` состоит только из `InitializeComponent()`, без циклов/условий/создания runtime controls/I/O/async. Если UI gate нельзя реально пройти, указать `UI_NOT_TESTED` отдельно.
