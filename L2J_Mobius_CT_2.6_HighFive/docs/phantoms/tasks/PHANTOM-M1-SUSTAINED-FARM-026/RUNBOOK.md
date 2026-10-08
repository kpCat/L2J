# Автономный runtime026

1. Verify current base/remote и зафиксировать все чужие runtime/PID read-only.
   Основные 2106/9014/7777 заняты чужими — использовать уже поддержанный alternate
   port комплект private runtime; если его нет — BLOCKED_ENVIRONMENT без kill чужих.
2. Разобрать025 Prepare/Control/Observe/Collector/Checks, переиспользовать значения.
   Runtime root .phantom-local/contract026<letter>/runtime, secret files private.
3. Lanes: a=TEST; b=short proof; c/d=final farm + persistence; e/f=isolated crash;
   g=death/return; h=дополнительная copy при повреждённом own input.
   Copy existing025c/d для воспроизводимых условий; источники immutable read/export.
4. Settings: target1280 / active8 / maxMaterialized8 / maxScheduled10000;
   diagnosticsON, SyntheticTrue, existing TestAdmin100/autoattach, GM hide/invis/
   invulnerable/silenceFalse. TestAdmin только копия. QoL AllowedAccounts не использовать.
5. Полный stock GameServer/NPC AI. Observer setup выбирает factual lane до baseline;
   native movement после baseline. Не перемещать Phantom/мобов, не выбирать ему
   target, не лечить/выдавать предметы. Guards/latency/data hashes записать.
6. Existing025 single heartbeat writer и commandcounter. Абсолютный TTL525с,
   heartbeat его не продлевает. Сцена360–420с + setup/stop должна помещаться.
   Для R06 взять отдельную session, не ждать expiry после предыдущей farm сцены.
7. Final cohort eight where available; минимум4 для отдельной сцены допустим ТОЛЬКО
   по заранее объявленному лимиту до baseline, не после failure. Основной regression
   сравнения025 целится8. Primaries назначаются до baseline; следующая сцена — другие.
   All baseline actors остаются в отчёте, включая missing/dead/epoch changed.
8. Sampling<=5s, no observer mutations. Missing actor требует lifecycle trace;
   не заменить actor новой успешной identity и не склеить counters разных epochs.
9. По первому incident сохранить exact trace+dumps; no hotfix live JVM. Следующий
   исправленный commit только stop→clean committed build→поднятие той же product
   clone при проверках durable continuation. Failed measurement не стирать.
10. Штатно закрыть Synthetic/session, остановить root admissions, дождаться drain,
    sealed witnesses и exact persistence, затем owned Game/Login. Существующий
    resolved checkpoint не запускать вручную для «спасения» profile; это production path.
11. На saved clone sameDB restart дважды; immutable before/after witnesses. Ordinary
    progression между измерениями объяснять, а не стирать к предыдущим цифрам.
12. Зависания запуска/команды имеют bounded timeout. UNKNOWN/UNCERTAIN не replay blindly.
    Проверить existing protocol state; закрыть только own session, записать FAILURE.
13. Финал: свои JVM/listeners0, database pending отдельно. JVMexit сам по себе не
    доказывает retained0 или CALLBACK_DRAIN_PASS. Raw dumps не коммитить с secrets.
