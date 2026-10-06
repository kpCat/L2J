# Границы и бюджет

## Что сохраняем

Только High Five. Mobius владеет движением, damage, cast, reward, pickup.
Рабочие исправления TASK013/016/018/020 сохраняются. b4 owned-store protocol,
обычный historical fence и запрет HISTORICAL_BASELINE на visible decisions сохраняются.
Никаких изменений Player/ThreadPool/EventDispatcher/AutoPlayTaskManager/
AutoUseTaskManager/GeoEngine/zone XML/SQL schema. Никакого второго combat/pathfinding engine.
kpCat, основной checkout и чужие diff не трогать.

## Что разрешено

Пять semantic production files перечислены в SOURCE_MAP.tsv. В них разрешены:
подготовка актуальной visible goal до вызова DecisionEngine; обработка terminal travel;
атомарная смена именно своего visible плана; local-only overload существующего planner.
В PhantomSystem можно добавить несколько скалярных census-полей привязки travel failure.
Новые independent suites разрешены в двух точных test paths, existing tests обновляются
только для регрессий. Никаких новых сервисов, схем, durable-компонентов, глобальных кэшей.

## Организация

До 90 минут активной работы, ожидание человека не входит в бюджет.
Ориентир 15 мин read/fixtures, 40 мин реализация/targeted checks, 15 мин build/runtime,
5 мин client observation, 15 мин cleanup/report. Это предел, не обещание сроков.
На 75-й минуте не начинать новые исследования. Одна реализация в этом диалоге;
нет автономной серии дополнительных fixes после runtime.

Ориентир расхода — до 80 000 суммарных токенов, если harness показывает эту метрику.
При отсутствии метрики не писать выдуманный расход. Не печатать целиком большие Java,
JSON, diff или логи. Читать точные методы, сохранять полный вывод в файл, показывать
summary/хвост. Не создавать десятки генераторов Python для каждого артефакта.
Достаточно одного runner и RESULT с приложенными evidence.

Нельзя останавливать задачу только потому, что terminal water failure требует согласовать
runtime revision или сменить локальный goal: это утверждённый scope. Нельзя расширять
scope на новый native writer, новую схему или неизвестный конфликт ownership.

## Публикация и изоляция

Проверить remote HEAD и ancestry. Required base должен совпасть, иначе отчёт о drift,
не работать на случайной ветке. Если ветка занята, разрешён detached isolated worktree:
`C:\Users\ZBook\.codex\worktrees\m1-local-farm-021\L2J_Mobius` от exact base.
Основной `C:\Users\ZBook\L2J_Mobius` не переключать.

Разрешены fetch/read, создание этого worktree, exact-path add/commit,
`git push origin HEAD:refs/heads/experiment/m1-candidate007-observe008` без force.
Запрещены add-dot, reset, clean, stash, rebase, force и push feature/phantom-world.
Task-файлы распакованы в основной root; их можно скопировать в isolated worktree.

Обычно два commit: implementation, затем итог/evidence. Не делать рекурсивные receipt
commits ради записи SHA самого отчёта. После финального push показать remote SHA текстом.
Прямое разрешение на эти Git-действия относится только к данной задаче.
