# Антисемичасовой рабочий процесс (из журнала LEARNING-002)

## Причина прошлых 7 часов (подтверждено прикреплённым логом)
- Legacy Windows PowerShell5/PSModulePath наследовался от PS7: `Get-Acl`/CMD отказ, исправлено.
- В Windows многократно проверялись WMI/CIM process exit races, cold worker exit receipts, fake orphan, JOB KILL_ON_CLOSE и handshake. Всё это теперь стало штатным кодом 002.
- Перед очередным Native были исправления спота, канонических anchor, нехватки безопасных целей и естественное восстановление HP (`~161.6 s` для одного Solo). Много батчей не давали полезных NATIVE событий.
- Полная модель была обучена за 33 ms, огромную часть времени заняли GameServer ~35s bootstrap и 300–600s native-эпизоды, многократные проверки/ревью и final delivery.

## Как не повторять
1. Git/ source audit 002 + preflight contracts. Зафиксировать в плане минимальную архитектуру V3 и две native HEAL альтернативы. Не проводить повторную 002-архитектурную ревизию и 7-parallel agents.
2. Сначала unit+mathematical smoke для V3 encoders/weights, action receipt and CPU bench. До GameServer уже должны быть clear legal source, skill, party, target, receipt, train/audit split и сухой сценарий.
3. Reuse existing training launcher, GameServer, MariaDB, scenes, owner. Ни одного нового способа запуска Windows/job/supervisor. Если инфраструктура без изменений, не тестировать заново closing Codex UI and forced crash.
4. Собственный live proof 1–2 native сценария (не жёсткий лимит). При отказе **diagnose before rerun**; не переключаться по кругу между Hot Springs, Imperial Tomb, Silent Valley ради одного искомого source. Ищите законный nearest verified heal situation, меньше свободы сценария — меньше неопределённости.
5. После первого живого HEAL proof и существующего DAMAGE smoke — offline train, fresh audit, cold reload, one safe native decision change. Не гонять миллион samples в 003; этого нет в acceptance.
6. Одна итоговая focussed regression + safe STOP, отчет/commit/push. Исторические 266/277 тестов запускать целиком максимум при финальном изменённом owner border, не на каждой локальной правке. Дешёвые tests — многократно по необходимости.
7. Каждый диалог Codex = одна новая задача; избегать 20 micro-splits и chained suffix. Не требовать у владельца ручной приёмки TRAIN_*.cmd сейчас. Отчёт по игровой механике, а не сырые Java-stacktraces как главный результат.

Количественных ограничений boot/JAR нет. Есть ограничение на бессмысленную повторяемость и разрастание scope. Цель быстрее не за счёт подмены NATIVE proof. При неожиданно сложном multi-owner issue сообщать точную причину и минимальное решение, а не уходить в переписывание всего Combat.
