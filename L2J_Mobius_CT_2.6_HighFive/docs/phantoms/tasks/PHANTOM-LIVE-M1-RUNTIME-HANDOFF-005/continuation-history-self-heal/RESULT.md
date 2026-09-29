# Результат continuation-history-self-heal

Статус: IMPLEMENTED_AND_DEPLOYED; final manual gate REQUIRED. M1 GREEN не заявлен, M2 не начат.
Код: `9e3576311af6ef8c53e662955e2330b7cee00143`, опубликован в `origin/feature/phantom-world`.

## Реализация

Обычный farm сохраняет encounter и deterministic prefix добычи в пределах текущего mutation envelope; излишек становится ground loss. Acquisition остаётся строгим. Типизированный farm input различает stale authority, position, target, resource, unsupported loot и unknown. Исторический service восстанавливает известные durable failures на прежних request/cursor и применяет refresh, replaceFromState или исключение текущего `npc@anchor` по причине. Планировщик сначала ищет ±2, затем расширяет только нижнюю границу до L−5 и L−10. При отсутствии достижимой цели канонический `HISTORICAL_IDLE` продвигает ровно минуту без награды или изменения персонажа. Ecology больше не превращает recoverable history failure в постоянный запрет спроса. Неизвестные и inconsistent failures остаются явными blockers.

Runner и Pilot уже не содержали глобального требования нулевого `FAILED_REPLAN_REQUIRED`; их не меняли. UI, GamePackage, schema, world data, geodata и другие chronicles не менялись. Дополнительные AGENTS/code-map/pattern файлы не найдены; Java 25/Ant, csproj/props/targets неприменимы. Прочитаны TASK/DESIGN/PLAN/ACCEPTANCE/HANDOFF/SOURCE_MAP, module AGENTS, README, build, relevant docs и локальные аналоги background/model/planner/ecology/runner. Переиспользованы существующие authority, transaction, operation key, `withPlan`/`retryRunning`, `replacePlan`, normal travel и guarded TEST DB.

## Проверка

RED focused regressions получены до реализации. После реализации focused: background-model 8/8, historical-background-goal033a 23/23, m1-runtime-handoff 15/15. Aggregate `phantom-m1-runtime-handoff-test`: 205/205 PASS, `BUILD SUCCESSFUL`, включая native/geodata маршруты. Clean detached exact-SHA `ant jar`: PASS. Review не нашёл blockers; замеченный пробел historical idle replay закрыт focused regression. Шесть SQL файлов в рабочем дереве имеют только EOL-различия для TEST manifest, не публиковались и семантически не менялись. Исходный checkout с чужими изменениями сохранён.

## PLAY и manual gate

Controlled deploy после остановки обоих owned servers и согласованного native PLAY dump: `artifacts/local-play/m1-005-backup-9e35763-20260929-203534`, `PLAY.sql` 63 502 509 bytes, SHA256 marker. Deployed GameServer JAR SHA-256 `FA609D7E5E3B8A269500F54D03C339C65C40A560569C7D5927FB0639EC7CD3EE`. LoginServer PID 7860, GameServer PID 16908; CONFIG/ownership PASS, 1280/64/128/100 и maxScheduled 10000 сохранены.

Read-only PLAY 2026-09-29 17:36:52 UTC: `READY=1280`, `RETIRED=8720`, retired pending 6641 и reserve ecology/history SHA256 `0659577b3b92f7708bebb3e4688a0d8ac9065714654e6736064b8343dffd1a68` сохранены. Active history: COMPLETE 501, RUNNING 229, FAILED_REPLAN_REQUIRED 550; до deploy baseline был 303/387/590. Причины: authority unsupported 93, stale 1, object cap 330, planner absent 116, item conflict canonical 10. Последние 10 остаются видимым unknown/inconsistent blocker, не исправлялись SQL. `activePending=779`, `currentHorizon=9`; worker/recovery работает. Нулевой глобальный failure count не требовался.

Свежий 10-минутный TestAdmin arm был подготовлен после health/snapshot, но не активирован настоящим клиентом до истечения 2026-09-29 17:48:59 UTC. `Get-LocalPlayPilot` после срока не подтвердил новую session. Connected runner не запускался, TestAdmin не перемещался, Pilot scene/census/restore не наблюдались. Для завершения M1 нужен новый свежий arm при присутствующем TestAdmin и ровно один запуск существующего `Run-M1RuntimeHandoff.ps1`; затем записать фактический connected результат. Если connected RED — остановиться на фактической границе. M2 запрещён.

## Scope и процесс

Публикация кода ограничена 10 production Java и 3 focused test Java, перечисленными в `git show --format= --name-only 9e3576311af`; архив задачи — 8 исходных файлов и этот RESULT. Bounded exception объяснён в SOURCE_MAP.tsv: `PhantomBackgroundGoalSpec`, `PhantomPopulationEcologyService`, uppercase progression hash validator. Иные artifact families не менялись.

Git применялся по явному разрешению TASK только для read-only baseline/scope и exact-path публикации: `git status --short`, `git rev-parse HEAD`, `git ls-remote origin refs/heads/feature/phantom-world`, `git diff --stat -- <exact paths>`, `git diff --check -- <exact paths>`, `git diff --ignore-space-at-eol -- <six SQL paths>`, `git diff --cached --name-only`, `git diff --cached --check`, `git show --format= --name-only HEAD`; `git add -- <21 exact task paths>`, `git commit -m "Recover historical phantom background failures for M1"`, `git push origin HEAD:feature/phantom-world`. Без branch/reset/restore/clean/stash/rebase/force. Этот RESULT и STATE публикуются отдельными exact-path командами.

mojibake-маркеры в изменённых файлах проверены.
escaped Cyrillic в изменённых файлах проверены.
