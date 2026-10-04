# TASK008 — наблюдение, а не исправление архитектуры

## Решение пользователя и приоритет

Новый запрос заменяет для008 старые task007 D5/closure/WORLD требования и запрет любого
клиентского наблюдения до полного engineering GREEN. Он **не** разрешает считать
непринятый кандидат production-ready. Старый task007 остаётся paused.
`goal.runtime 11→12` — версия данных, не следующий milestone; её сейчас не исправлять.

## Единственная цель

Запустить точные сохранённые исходники candidate007 в изолированном локальном runtime,
войти настоящим TestAdmin и наблюдать игру10–15 минут. Проверить текущий штатный путь
PhantomVisibleAutoPlay → AutoPlay/AutoUse → PlayerAI/native actions. На одном выбранном
фантоме попытаться получить5 последовательных farm cycles без помощи оператором.

**AI/production source modifications = 0.** Допускается только механическое восстановление
замороженного кандидата и небольшой ремонт локальных средств запуска/наблюдения.
Ни новый Brain, ни новый native hook, ни изменение thresholds/guards в008 не разрешены.

## Изоляция и разрешения

- Репозиторий `kpCat/L2J`; только модуль High Five; base `3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25`.
- Разрешено новое изолированное worktree:
  `C:/Users/ZBook/.codex/worktrees/m1-observe-008/L2J_Mobius`.
  Если этот путь уже занят чужим состоянием — не удалять/переиспользовать вслепую.
- Ветка только для эксперимента: `experiment/m1-candidate007-observe008`.
  В неё разрешены exact-path snapshot commit и normal push, затем docs/tooling-result
  commit/push при GREEN/BLOCKED/FAILED. В `feature/phantom-world` ничего не сливать.
- Snapshot commit публикует **UNACCEPTED EXPERIMENT**, а не production fix.
  Он нужен для чистой сборки из воспроизводимого codeSHA; требования final M1 не обходятся.
- Источник — committed `candidate-paused/SOURCE_MANIFEST.tsv` из task007 и его full files,
  не произвольные последние файлы старого worktree. 161 записей:103 integrated +58 duplicates.
  Все103 integrated entries включить, в том числе test/build/byte-only prerequisites;
  58 `.pending` не включать в executable tree. Не переносить предложения.
- Старые MAIN, worktree006/007, чужие diff, приватные дампы и журналы не менять.
- `l2jmobiush5_localplay3` — только чтение/export. `l2jmobiush5_phantom_test` не использовать.
- Разрешена только новая БД `l2jmobiush5_localplay_observe008` на127.0.0.1:3308.
  Когда исходный LoginServer использует другую локальную БД, для его копии разрешено
  второе имя `l2jmobiush5_localplay_observe008_login`. Иначе использовать одну копию.
  Имена должны отсутствовать либо иметь проверенное ownership именно008; чужую БД
  не перезаписывать. Нет DROP/очистки PLAY/TEST, нет ручной нормализации персонажей.
- Экспериментальные файлы: `<new-module>/.phantom-local/observe008/`.
  Только cloned DB получает штатные игровые записи. После наблюдения копию сохранить
  приватно для анализа; её состояние не переносить обратно в PLAY.

## Что разрешено в tooling

Переиспользовать существующие LocalPlay scripts/Pilot/`D:/Tools/L2ClientTools/L2.cmd`.
При необходимости создать до4 небольших task-local PS1:
`tools/Prepare-Observation.ps1`, `tools/Observe-Client.ps1`,
`tools/Capture-ClientWindow.ps1`, `tools/Stop-Observation.ps1`.
Внешний UI tool: до2 существующих текстовых файлов, непосредственно вызываемых L2.cmd,
с фиксацией exact paths и backup перед изменением. На ремонт — суммарно15 минут.
Нельзя править Java/classes/JAR, server-side consent/protocol/AI, геодату, игровые данные
или правила eligibility под видом tooling. Перезапуск после исправления транспорта —
не более1, только в оставшемся бюджете. Плохое поведение ботов не основание rerun.

## Управление временем

Лимит всей задачи — 90 минут. Один исполнитель; 0 субагентов;
0 общих/параллельных review; 0 full WORLD/regression suites.
Разрешены сборка, проверка manifest/изоляции, дешёвые syntax/transport checks и runtime.
К 55-й минуте должен начаться реальный клиентский эпизод; иначе BLOCKED и cleanup.
На 70-й минуте — только cleanup/report/publication. К 90-й минуте закончить задачу.
Если безопасная остановка не завершена, не бросать активные процессы молча: зафиксировать
точный owned PID/DB/опасность и выполнять только ограниченный cleanup, без новой инженерии.
Краткий статус каждые15 минут: стадия, минуты, достигнутый наблюдаемый результат, blocker.

## Git safety

Разрешены scoped read/fetch/status/diff/show/log/rev-parse/worktree add, exact-path add,
commit и normal push указанной экспериментальной ветки. Никаких add ., reset, clean,
stash, rebase, force, broad restore, массового форматирования, изменения чужого index.
Перед push проверить remote/ancestry; при конфликте сохранить локальный результат,
не переписывать историю. Private config, passwords, arm tokens, dumps/binaries не публиковать.

## Endpoint

Опубликовать RESULT/HANDOFF и минимальное evidence. Статусы разделять:
`TASK_RESULT=GREEN|BLOCKED|FAILED`, `RUNTIME_BEHAVIOR=PASS|PARTIAL|FAIL|NOT_OBSERVED`,
`AUTOPLAY_5_CYCLES=PASS|FAIL|NOT_OBSERVED|NOT_REACHED`, `M1=OPEN`.
GREEN задачи допустим при полностью выполненном честном наблюдении даже с FAIL поведения.
Если сервер/клиент не удалось поднять — это BLOCKED, не «M1 протестирован».
Не продолжать автоматически после отчёта и не назначать себе следующий fix.
