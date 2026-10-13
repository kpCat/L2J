# HANDOFF041 — публикация, компактный review и финальная приёмка

## 1. Ограниченный набор документов

В текущем семействе041: `CHECKPOINT041.md`, `FIX_REVIEW041.md`, `GATES041.tsv`, `RESULT041.md`, `REAL_FINAL041.md`. Не создавать separate report на каждый timestamp. Новые общепроектные release contract/operator docs — DELIVERY.md.

Private ops041: один RUNTIME041.json, GIT041.jsonl, EVIDENCE_INDEX041.tsv, tests/raw конечных эпизодов. Существующие схемы receipts переиспользовать. Никаких proof-of-index/proof-of-proof отчётов.

GATES columns: gate/status/candidate_id/episode/concrete_outcome/evidence_relative_path/scope_or_blocker; старые/negative/invalid не смешивать сfinal PASS. Каждый закрытый gate ссылается на реальное наблюдение или выполненный тест, не обещание следующего запуска.

## 2. Git: все полезные изменения должны попасть в ветку

Разрешены точечные read-only status/rev-parse/branch --show-current/worktree list/log/show/diff/ls-files/check-attr/ls-remote/merge-base и git archive только текущего commit/HighFive в собственный staging. Сами commands логировать один раз exact argv/UTC/exit в GIT041.jsonl, без отдельного audit проекта.

Запись: `git add -- <exact allowlisted paths>`, ordinary commit, `git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008`. Просмотреть staged diff и scope; чужой staged/untracked не трогать. Не `add .`/`add -A`, не force/reset/clean/stash/rebase/restore/amend, не удалять branches/worktrees и не делать merge в MAIN. Remote drift не разрешает force push.

Кодовые checkpoints допустимы после законченного RED→GREEN блока. Final build из известного CODE_COMMIT; REPORT_COMMIT может быть позднее doc-only. Не коммитить hash самого будущего коммита и не запускать новую бесконечную публикацию ради receipt. Final publication receipt вне self-reference указывает actual local/remote SHA.

Проверить отсутствие source fix только в ignored path, private runtime config или cached jar. Каждая полезная reusable deployment правка tracked. UTF-8/BOM/EOL существующего сохраняются; отдельно проверить mojibake и escaped Cyrillic, не экранировать русский текст вместо его исправления.

## 3. Один небольшой review ZIP, отдельная поставка

Создать `TASK041-review.zip` в OWN task041. Ориентир ≤1000 entries и≤250MiB, не hard game gate и не разрешение терять нужные данные. Включить:

```
TASK041-review/
  RESULT041.md
  FIX_REVIEW041.md
  GATES041.tsv
  REAL_FINAL041.md
  CHECKPOINT041.md
  changes/            # exact diff + изменённые code/tests/config/templates/helpers
  tests/              # RED/GREEN/negative/final summaries
  evidence/           # необходимые целые закрытые final streams и boundary cuts
  EVIDENCE_INDEX041.tsv
  RUNTIME041.redacted.json
  PUBLICATION041.json
  RELEASE_CONTRACT.md
  BUILD_AND_OPERATOR_CHECKS.md
  MANIFEST.sha256
```

Исходный raw для каждого ключевого вывода либо самодостаточно включён, либо указан честный exact local path/hash и предел независимого replay. Crash boundary/recovery records и normal complete tails сохраняются, нельзя заменить их «всёPASS» summary. Несколько сжатых streams вместо файла на каждый event. Не включать весь039/040ZIP, DB dumps, весьworktree, client/JDK/geodata/private credentials/fault binary distribution в review.

Runtime поставкаМ1 — отдельный каталог/ZIP по DELIVERY, не обязана помещаться в250MiB review. Не отказываться включать продукт в runtime ZIP из-за того, что в review запрещены binaries. Оба вида артефактов имеют разные назначения.

Закрыть/flush выбранные inputs до упаковки. Manifest не содержит себя и внешний hashZIP. После создания заново открыть: безопасные относительные уникальные paths, CRC и SHA256 всех included files. Один внешний checksumZIP достаточен. Неудачный ZIP сохранить как failed, не выдавать за final. После настоящей приёмки допускается одна обновлённая версия TASK041-review-final.zip, не архив на каждый Status.

## 4. Итог автономной части

Коротко по-русски: TASK_RESULT/M1/READY_FOR_M2; что фактически исправлено; G0–G8/O/R1; tests; CODE_COMMIT/report SHA/push; точный путь runtime/review; что произошло с protected040a и новыми test lanes; actual wall-clock. Не писать «12часов» как факт при пяти часах работы.

Если всё серверное PASS: «Серверная часть М1 готова, осталась ваша окончательная проверка на новой041 сборке». M1=WAITING_REAL_FINAL, READY_FOR_M2=false. Указать одну проверенную команду входа и START/STOP/STATUS пути. Оригинальный040a не выключать и не направлять человека по старомуLogin на него вместо041.

Если долг остался, назвать точно и не выдавать preview за final. Исправимая причина при оставшемся времени не основание закончить на этом; продолжать до handoff по заданному лимиту/безопасности.

## 5. Последнее действие — пользователь, затем фиксация CLOSED

REAL_FINAL041 заранее содержит короткий10–15минутный маршрут проверки без Zariche. Обычный вход/движение/NPC/бой фантомов, away/return, logout/relogin observer. Не требовать долгого stress/manual QA и десятков команд. Пассивные bounded records собираются штатно без активного агентского polling во время ожидания.

После явного ответа человека проверить O_REAL/REAL_FINAL и все оставшиесяgates. Только тогда TASK_RESULT=M1_CLOSED/M1=CLOSED/READY_FOR_M2=true, final doc-only commit/push и final review. Не начинать М2. Финальную041 пару оставить работающей, пока пользователь не остановит её сам.

При необходимости доработки ordinaryМ1 после личной проверки это продолжение041, а не автоматический новыйTASK042; остаток автономного бюджета известен. После исчерпания бюджета не делать непроверенныйfix и не обещать скрытую работу. Истечение agent goal никогда не выключает принятуюruntime.

## 6. Cleanup без разрушения

Только штатно остановленные собственные test lanes, fault disarmed, schemas/raw/worktree сохранены. Не delete/drop/clean старое и не завершать чужиеJVM. Защищённый040a и один новый041final могут одновременно работать — это правильно, не ошибка «лишняя пара».

Завершающий read-only census привязан к incarnation/root/schema, не к историческому PID. Никаких постоянных Services/ScheduledTasks для фонового агента, никаких скрытых injector/premains в acceptance. Отдельно подтвердить пользовательскую самостоятельность runtime и отсутствие зависимости от открытого Codex.
