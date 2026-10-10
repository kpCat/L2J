# Установленные факты, гипотезы и вопросы

F1 — FACT/source: startVisible/awaitVisible вызывают visibleStop и возвращают один
reason background.visible.replan_required при любом false из _visibleSuitable.
F2 — FACT/source: visibleFarmReady схлопывает cooldown, protocol, route, target ban,
stored-goal mismatch в boolean. По внешнему reason нельзя выбирать fix.
F3 — FACT/source: pre-work prepareVisibleDecision владеет reload, publication и recovery;
в handler это делать небезопасно. Existing CAS/receipt надо переиспользовать.
F4 — FACT/source: VisibleFailures _steps/_targets имеют120s TTL, но точные _route/
_protocol predicates проверяют goal/revision/epoch, не время. Это не автоматическая
ошибка: terminal protocol может быть обязан оставаться fenced. Не убирать всё по TTL.
F5 — FACT/source: VisibleEpisode.unavailable задаёт cooldown max(now+10s,started+60s).
Нужен фактический caller и момент, а не гипотеза «таймаут мал».
F6 — FACT/report: текущие43/117 не прошли непрерывность; настоящие награды есть у всей8.
F7 — FACT/report: final ordinary hooks без SQL, first restart8/8; второй не разобран.

H1 — HYPOTHESIS: route/exclusion/episode может законно остановить session, но не иметь
работающего выхода в новый eligible plan. Проверить producer→recovery→последующие ticks.
H2 — HYPOTHESIS: новая цель опубликована, handler ещё использует старую revision либо
cooldown относился к иному plan. Проверить exact versions и owner во ВСЕХ переходах.
H3 — HYPOTHESIS:117 имеет иной ресурсный/навигационный разрыв, не тот же guard что43.
H4 — HYPOTHESIS: второй restart показывает законное движение между двумя снапшотами.
Найденный projection receipt может быть только последним metadata-only звеном;
он сам по себе не разрешает и не объясняет изменение XYZ.

Отчёт координатора — read-first аудит опубликованного source и scalar reports.
Private raw/local runtime031 недоступны координатору в этой сессии; не приписывать ему
воспроизведение43/117, crash или full SQL comparison. Это первые действия исполнителя.
