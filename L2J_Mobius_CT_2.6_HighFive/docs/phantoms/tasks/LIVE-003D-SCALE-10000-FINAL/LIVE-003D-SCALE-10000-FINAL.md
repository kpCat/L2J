# LIVE-003D — финальный scale 10000 gate

## Статус

**BLOCKED — `SCALE_10000_DEADLINE_BLOCKED`.** Target 10000 managed / 10000 linked не достигнут за 45 минут после готовности GameServer. LIVE-003 не закрыт как GREEN; 15-минутный soak не начинался.

## Scope и изменения

Начальные branch/HEAD: `feature/phantom-world`, `5cfc38b1ea14197050d23872e573e111849a074e`. Принятый production code commit `a4bb55270d33bfdd0ce96e0367dd509b5c3358b2`; private GameServer.jar SHA-256 `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`. Из-за уже принятого JAR новый build не проводился, сторонние dirty-файлы рабочего дерева не использовались.

Единственное runtime изменение: private `artifacts/local-play/runtime/game/config/Custom/PhantomPlayers.ini` — `PhantomPopulationTarget = 5000` → `10000`; private `artifacts/local-play/runtime/local-play.json` — mirror `populationTarget = 5000` → `10000`. Backup обоих файлов сохранён. Сравнение с backup доказало ровно эти две подстановки; 90-файловая hash inventory не обнаружила другого drift. ActiveTarget=64, MaxMaterialized=128, MaxScheduled=10000, CreationInFlight=2, pulse=100 ms, boundaries-per-pulse=64 сохранены. Схема БД, миграции, production-код, generator-library и другие хроники не менялись.

Task-owned outputs: `EVIDENCE.md`, `STATE.md`, этот отчёт и `LIVE003D_RUNTIME_10000.tsv`; подробные read-only logs и локально переиспользованные monitoring scripts — `.phantom-local/logs/LIVE-003D-SCALE-10000-FINAL/`. PLAY получала записи только через штатный runtime; прямых DML/DDL/reset/reseed/delete не было.

## Результат runtime

Baseline: LocalPlay STOPPED, все порты закрыты, PLAY 5000 managed / 5000 linked / 5000 READY, 5000 уникальных имён и аккаунтов, дубликатов 0; committed background positions 4978, ecology 5000.

Канонический `Start-LocalPlay.ps1 -Background` запустил owned Login PID 14096 и Game PID 24844. GameServer загрузился и зарегистрировался в LoginServer в `2026-09-26T20:51:31.679+03:00`. Порты 2106/9014 принадлежали Login, 7777 — Game. Ramp deadline: `21:36:31.679+03:00`.

В точке deadline read-only SELECT показал 9326 managed / 9324 linked, 9325 READY на отдельном чтении, 9324 уникальных имён и аккаунтов, дубликатов 0/0. Недостача к target: 674 managed и 676 linked. Прирост 4326 managed за 45 минут = 96,13 managed/min при необходимых 111,11 managed/min; принятый LIVE-003C дал 202,82 managed/min на своём участке. Поздний ряд показывал продолжающийся, но недостаточный рост: 6946 managed в `21:07:00`, 8130 в `21:21:10`, 9326 в `21:36:31`. Доказанной внутренней причины замедления нет; крупный ремонт запрещён scope этой задачи.

49 samples: heap 80,32–93,75% из 4096 MiB, максимальная серия выше 90% — 2; threads 160–164; DB connections 13–15/151; fatal/OOM markers 0; duplicate names/accounts 0. Background committed positions 4978 → 8938 на deadline, ecology rows 5000 → 9326: фон продвигался, его покрытие оставалось неполным. Время достижения 10000 и время soak отсутствуют, потому что target gate не прошёл.

Штатный `Stop-LocalPlay.ps1` остановил только owned PID. Итоговый checker: Login/Game STOPPED, `staleRecord=False`, 2106/9014/7777 закрыты. Post-stop PLAY: 9352 managed / 9350 linked, 9350 READY и 2 незавершённые записи, max profile ID 9352; уникальные имена/аккаунты 9350/9350, дубликаты 0; background.state/catchup 8961, ecology 9352. Дальнейшее создание во время stop не меняет deadline result. Все профили сохранены, private target оставлен 10000.

## Проверки и ограничения

- Штатные Check/Start/Stop-LocalPlay, read-only `SELECT`/`SHOW`, JDK `jcmd GC.heap_info`, owned PID/port checks, fatal marker scan и 49-row TSV выполнены. Новые автоматические тесты не добавлялись: production-код не менялся, проверка здесь была live runtime gate.
- `private-hashes-after.tsv` и `private-hashes-final.tsv` совпадают по всем 90 private runtime файлам. Protected settings остались исходными; private backup сохранён.
- Mojibake-маркеры в 9 изменённых текстовых файлах проверены: 0 совпадений.
- Escaped Cyrillic в 9 изменённых текстовых файлах проверен: 0 совпадений.
- Точные команды Git и SHA итогового report commit отражаются в итоговом ответе после commit/push: SHA собственного commit нельзя достоверно встроить в этот же commit. Следующий этап — отдельная bounded диагностика падения creation throughput; LIVE-004/005 не начаты.

## Git и передача

Разрешённые task-команды Git, использованные для branch/HEAD/scope guard и публикации этого отчёта:

```text
git branch --show-current
git rev-parse HEAD
git status --short
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
git diff --check
git add -- "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/EVIDENCE.md" "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/STATE.md" "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/LIVE-003D-SCALE-10000-FINAL.md" "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/LIVE003D_RUNTIME_10000.tsv"
git diff --cached --check
git diff --cached --name-only
git diff --cached --stat
git diff --cached -- "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/EVIDENCE.md" "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/STATE.md" "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/LIVE-003D-SCALE-10000-FINAL.md" "L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/LIVE-003D-SCALE-10000-FINAL/LIVE003D_RUNTIME_10000.tsv"
git commit -m "phantom(live-003d): record 10000 deadline blocker"
git push origin feature/phantom-world
git rev-parse HEAD
git status --short
```

В commit входят только четыре перечисленных output-файла этого task. Изменённые ранее несвязанные tracked/untracked файлы не включаются. Итоговый report commit/HEAD и результат push указаны в финальном ответе.

Первый exact-path `git add` внутри sandbox не смог создать `.git/index.lock` (`Permission denied`); повтор той же команды с повышенным доступом успешно добавил четыре разрешённых файла.
