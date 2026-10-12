# HANDOFF040 — рабочий сервер и небольшой пригодный review

## 1. Что отправить пользователю после автономной части

Короткий итог по-русски, примерно до40 строк: фактический TASK_RESULT/M1/REAL_FINAL; что починено в игре; G0–G8 и O040_SERVER; число фактических checks; final code/report SHA и push; состояние запущенного acceptance/preview сервера; точный путь к review ZIP; что требуется пользователю в последнем шаге. Не заявлять12часов если реально работал меньше; wall-clock и goal-counter не смешивать.

При сохраняющемся crash-запрете прямо сказать: G6 остаётся BLOCKED_AUTHORITY, поэтому это PREVIEW/M1_OPEN, даже если все прочие части работают. Не формулировать «готово, осталась мелочь» или «М1 закрыт кроме…».

## 2. Ограниченный набор выходных документов

В OWN `docs/phantoms/tasks/PHANTOM-M1-FULL-CLOSEOUT-040/`:
- `CHECKPOINT040.md` — один компактный текущий ledger: старт/остаток бюджета, exact current candidate, current plan step, authority, критичные blockers. Обновлять, не дописывать сотни страниц tick-log.
- `FIX_REVIEW040.md` — причины/RED→fix→GREEN, реальные changed files/методы, adversarial self-review и оставшийся риск в одном документе.
- `GATES040.tsv` — одна строка на gate/подсценарий: `gate,status,candidate_id,episode,concrete_outcome,evidence_relative_path,scope_or_blocker` (разделительTAB). Исторические/старыеcandidate результаты явно отличать отfinal.
- `RESULT040.md` — фактическая итоговая сводка, связь CODE_COMMIT/JAR/config и final report publication, инструкция продолжения.
- `REAL_FINAL040.md` — короткая инструкция пользователя плюс фактический клиентский результат, сначала NOT_RUN. Не заполнять предполагаемым согласием.

В private `.phantom-local/ops040/` или существующей выделенной visualization040 папке:
`RUNTIME040.json`, `EVIDENCE_INDEX040.tsv`, `GIT040.jsonl`, test summaries и конечные raw streams. Формат существующих receipts переиспользовать. Не добавлять документы «проверка индекса», «проверка проверки», «proof of proof».

## 3. Компактный TASK040-review.zip

Один архив по автономному handoff, не архив на каждый probe. Состав:

```text
TASK040-review/
  RESULT040.md
  FIX_REVIEW040.md
  GATES040.tsv
  REAL_FINAL040.md
  CHECKPOINT040.md
  changes/                 # exact итоговый diff + только изменённые source/test/helper файлы
  tests/                   # сводки suites и logs причинных RED/GREEN/negative cases
  evidence/                # минимальные самодостаточные rawstreams/срезы для final gates
  EVIDENCE_INDEX040.tsv
  RUNTIME040.redacted.json
  PUBLICATION040.json      # final published SHA и проверкаremote, без секретов
  MANIFEST.sha256
```

Архив должен позволять ревьюеру увидеть изменения и проверить ключевые выводы без876336 файлов. Упаковывать события как несколько закрытых/сжатых streams, а не файл на каждую запись. Raw, достаточные для конкретного gate, включить целиком либо честно обозначить externally-retained часть с точным локальным путём и hash. Ссылку на внешний огромный raw нельзя выдавать за самодостаточный небольшой доказательный файл.

Ориентир: до2000 entries и до250MiB. Это ориентир компактности, НЕ новый gate игры и не разрешение терять нужные данные. При большем объёме оставить полный raw локально и включить конкретные причинные/итоговые срезы плюс индекс; объяснить предел воспроизводимости архива. Не делать ещё один монолитный backup всего worktree/DB.

Не включать039 ZIP внутрь040 ZIP, все старыеepisodes, runtime distribution/JDK/client/geodata, DB dumps, auth secrets, foreign files, бинарные зависимости или весь Git. Не извлекать/перехешировать весь876336-entry input, если задача использует лишь небольшой его набор: проверить старый receipt/index и байты конкретных используемых источников. Не называть это повторной полной проверкой039 archive.

До упаковки закрыть/flush все выбранные файлы и manifest. Manifest не содержит самого себя/собственныйZIP hash. Затем создатьZIP, заново открыть, проверить безопасность/уникальность путей, CRC и SHA256 ВСЕХ действительно включённых файлов против manifest. Один внешний SHA256 самогоZIP достаточен; не создавать цепочку self-referential receipts. FailedZIP не удалять/не выдавать заverified.

После настоящего клиентского CLOSED допускается одна небольшая обновлённая версия `TASK040-review-final.zip`; не серия новых архивов на каждую строку отчёта.

## 4. Git — точечно, без опасного восстановления

Разрешены read-only `status`, `rev-parse`, `branch --show-current`, `worktree list`, `log`, `show`, `diff`, `ls-files`, `check-attr`, `ls-remote`, `merge-base` по необходимости. Все Git invocation с точными argv/UTC фиксируются одним private GIT040.jsonl, не отдельным proof-package.

Write: `git add -- <полный точный список разрешённых файлов>`, обычный commit только собственной работы039→040 и обычный `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`. До commit просмотреть staged diff и список путей. Foreign staged/untracked не коммитить и не менять; при смешанном чужом index не очищать его автоматически.

Запрещены `git add .`, `git add -A`, reset/clean/stash/rebase/force, checkout/restore чужих файлов, commit --amend, удаление refs/worktrees и широкое очищение index. Никакого force-push при remote drift. Не использовать generic clean-target, который удаляет protected runtime/raw.

Кодовые checkpoints разрешены для причинных фиксов и чистого exact-SHA build. Не коммитить каждый лог/таймер. Final code commit известен до последнего сборочного теста. Doc-only публикация после проверок может иметь другой HEAD: явно хранить CODE_COMMIT и REPORT_COMMIT, не пересобирать продукт из-за timestamp отчёта.

Final report не пытается включить собственный будущий SHA: внешний PUBLICATION040.json фиксирует итоговый commit, push result и actual remote SHA. Не запускать новый commit только ради сохранения hash предыдущего receipt. Проверить local=remote после normal push; при неудаче честный PUBLICATION_FAILED, не скрытый GREEN.

В каждом исходе проверить UTF-8/BOM/CRLF существующих изменённых файлов, mojibake-маркеры и escaped Cyrillic. Исходный формат сохранять, нечитаемую кириллицу не заменять бессмысленным escaping. Новый Markdown/TSV — UTF-8.

## 5. Финальный сервер не выключать

Штатно остановить собственные тестовые GameServer/LoginServer и контроллеры завершённых episodes, убедиться в разоружении всех fault mechanisms (по умолчанию они не запускались). Никаких wildcard kills, чистки БД/диска или остановки MariaDB/чужих процессов.

Оставить ровно один verified acceptance/preview LoginServer+GameServer и необходимый пассивный observer/его штатный worker. Exact allowlist: PID, start-time/incarnation, executable/command-line, runtimeRoot/runtimeId, listening ports, schema, CODE_COMMIT/JAR/config hashes. Если observer внутри JVM, не выдумывать отдельный PID. Законченные test wrappers не должны владеть финальными PID через finally/trap/on-exit cleanup.

Использовать existing запуск/status/stock-stop инструмент. Когда нужен адаптер, создать один private `Control-Acceptance040.ps1` с `-Action Start|Status|Stop`: reuse готового механизма, exact ownership check; Stop только штатный. Status не должен restart/создавать actors/изменять DB. Не устанавливать новые службы/автозагрузку/задания Планировщика, не менять firewall. Режим Start не запускается повторно при уже живом owned runtime.

Финальный observer не требует активного 12-часового shell-polling от Codex. Логи ограничить существующим безопасным режимом; если exporter достиг лимита, явно signal stop recording/неполноту, а не переполнять диск или менять cap. Не удалять старые raw для освобождения места. Продукт не зависит от audit exporter.

Проверить late shutdown/TTL ловушки: выполнить status после прекращения test-control и убедиться, что passive runtime здоров. Нельзя молча продлить lease чужого process/consent; конечные Synthetic/control TTL сохраняются. Runtime обязан ждать normal REAL, а не бесконечно исполнять test actions.

При известной опасности сохранения/corruption или identity conflict не оставлять такой runtime и не приглашать на финальный вход. Штатно остановить только собственный нерабочий episode и назвать конкретную границу. Здоровый preview при остальных незакрытых gates оставить можно с соответствующей маркировкой.

## 6. REAL_FINAL040.md — что должен получить пользователь

Сначала 4–6 простых строк: сервер запущен; какой проверенный клиент/аккаунт из существующей private конфигурации использовать; observer привяжется сам; что именно посмотреть; как сообщить результат. Пароли и токены в Git и общий архив не помещать. Не просить повторно информацию, которую можно найти в действующих LocalPlay/client tools.

Не выдумывать имя аккаунта, порт, spawn или готовый LoginServer, пока это не проверено. Дать точный private путь к рабочей login-инструкции и одну готовую status-команду; стандартный игровой вход остаётся действием пользователя. Ранее указанные `D:\Tools\L2ClientTools` читать, а не менять без доказанной потребности.

Пользователь заходит и наблюдает. Server-side REAL binding и его явное подтверждение записываются в реальный результат, не только automatic counters. После подтверждения и всех PASS — doc-only закрытие M1/push; сервер продолжает работать. Ожидание ответа не является отдельной автоматизацией или обещанием работать в фоне: агент завершает текущий turn и продолжает по следующему сообщению.
