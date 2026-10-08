# TASK027 — завершение native lifecycle, а не повтор TASK026

Required base: `882afb37821bdc5d7b8ec4e982e4e1e2c411cbbf`. Branch: `experiment/m1-candidate007-observe008`.
Модель/мышление: GPT-6.1 Sol / Very High. Новый Codex-диалог, без субагентов.

Цель пользователя: зайти и увидеть персонажей, которые продолжают играть, восстанавливаются
после смерти и не теряют заработанное. Не строить новый combat engine, не запускать M2.

TASK026 дал 3/8 в каждой сцене. Конкретное исправление этой задачи — полный путь
`завершение native действия → recovery/cleanup → store → продолжение/штатная остановка`.
Не повторять ещё один общий «sustained farm closeout» без исправления этого контракта.

## Существенная новая находка координатора

В сохранённом B java0.log initial shutdown вернул incomplete за 33ms, final за 17ms;
materializationService всё ещё RUNNING. Затем stock ThreadPool был остановлен и
работавшая recovery получила NATIVE_WORK_DRAIN_INTERRUPTED.
В PhantomSystem.shutdownIfStarted bounded wait есть только для ecology. В
shutdownClaimed `backgroundReadyForMaterializationShutdown()==false` сразу приводит
к FAILED/return до вызова materialization.shutdown.

Это доказанный дефект контракта ожидания, но не доказательство причины исходной
задержки EVENT24354. Разбирать их отдельно. Не объявлять callback starvation установленным.

Пакет не содержит готового применённого патча. source-excerpts — реальные выдержки
базы; proposals — исполняемая таблица решений и read-only анализатор, не server acceptance.
