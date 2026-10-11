# GOAL036

## Зафиксированная база и полномочия
- Repository: `kpCat/L2J`; только `L2J_Mobius_CT_2.6_HighFive`.
- Required HEAD и remote на старте: `c0e86fa2950012ae2ee898b841524fa160869a77`.
- Push: `origin HEAD:refs/heads/experiment/m1-candidate007-observe008` — normal, non-force.
- OWN: `C:\Users\ZBook\.codex\worktrees\phantom-dev\L2J_Mobius`.
- MAIN: `C:\Users\ZBook\L2J_Mobius` — read-only, кроме уже выполненной пользователем распаковки.
- Новый чат, GPT-6.1 Sol / Very High. Без субагентов. Существующий worktree сохранить; новый не создавать.

## Результат
Установить состояние native earned cast после исправления035. Если есть текущий дефект,
воспроизвести его на неизменённом base, исправить в разрешённой цепочке и доказать нормальное
завершение callback, сохранение заработанного и освобождение точного owner.
Если после035 дефект не воспроизводится, НЕ выдумывать semantic-правку: доказать текущую цепочку
реальными cast/drain-тестами и live-сценарием. Исторический cause27 остаётся UNKNOWN без новых доказательств.

Это **диагностика + исправление + проверка**, а не остановка после первого false или первого нового теста.
Несколько непосредственно связанных исправлений publication/start/completion/drain разрешены после
отдельных причинных RED. Переписывать combat engine, shared pools или persistence запрещено.

## Уже решено
035 сохранил252 предмета36/45 при лимите250 и дал FINALIZED/reload/healthy drain без Synthetic.
Это не доказательство поведения активного caster на момент игрового shutdown.
Не менять три production-файла inventory035 и не возобновлять запрос инвентаря1000.

## Автономность
Не спрашивать «в игре», «вышел», arm или подтверждение очередного in-scope этапа.
Аудит/удаление worktrees ПРИОСТАНОВЛЕН: не переключаться на него.
Если реально нужны недоступные credentials, доступ или изменение вне scope, сохранить точную причину,
выполнить безопасный cleanup и BLOCKED; не повторять вопросы автоматическими continuation.

## Время
Ориентир2–4ч; максимум360мин wall-clock, включая подготовку и отчёты.
После270мин — semantic freeze. Последние30мин — cleanup/publication.
Первый собственный full GameServer до60мин, либо конкретный INPUT/BLOCKED с уже выполненными проверками.
Не более4 новых production Jar builds и6 full GameServer starts. Native test JVM учитывать отдельно,
не называть полный GameServer «helper» ради обхода лимита. Полные 380s-сцены максимум2.
60–90мин — краткий checkpoint в PROGRESS036, не новый запрос пользователю.

## Границы
Игровые тела damage/MP/reward/skill legality и классификация EARNED не упрощаются.
Нельзя отменить RESERVED EARNED только ради outstanding0, вручную complete ticket, продублировать
callback при неизвестном Future, принудительно снять lease или очистить _failure.
TRUE FAILED shutdown остаётся FAILED, даже если поздний cleanup освободил память.
Сохранить 035, 033R, exact epoch/owner/goal, max250, pop1280/slots8/scheduled10000 и все исходные deadlines.

## Финал
Commit+push при любом исходе. RESULT_HANDOFF036, EVIDENCE_INDEX036, CLEANUP036,
проверенный внешний архив. Own JVM STOPPED; WORKTREE и DB сохранены.
GREEN_CAST_DRAIN / GREEN_CURRENT_PATH / PARTIAL_VERIFIED / BLOCKED / FAILED — по ACCEPTANCE.
M1 остаётся OPEN. Никакого REAL_FINAL, новой задачи или бесконечного ожидания пользователя.
