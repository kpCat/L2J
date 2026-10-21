# ACCEPTANCE065 — что означает серверная готовность присутствия

| ID | Обязательный результат | Что НЕ является PASS |
|---|---|---|
| P0-PREFLIGHT | SHA пакета, baseline, защищённые resource snapshots, своё OWN и актуальные current Test/World guards проверены | Отсутствующий manifest/подмена protected env |
| P1-DEMAND | Детерминированный native human-local demand передаётся через настоящее `Locality → Ecology → Readiness → Scheduler`; одна и та же идентичность при обновлении | Буквально добавить профиль в `World`, вызвать чужой `materialize` напрямую |
| P2-CAP | При заполненном лимите ранее невидимый и **действительно безопасный** материализованный профиль освобождается штатным soft cleanup, а eligible local профиль проходит штатный admission за ограниченные scheduler pulses; full cap не превышен | Поднять cap, выдать SUCCESS после записи intent или освободить занятого native actor |
| P3-GUARDS | Действительно видимый, current human-local, real-party, combat/move/cast/teleport, Store/reservation, accepted journey, persistence in-flight и wrong incarnation НЕ могут быть вытеснены. При отсутствии жертвы явно WAIT_CAPACITY | «Подождал до таймаута» и очистил чужие права или принудительно удалил Player |
| P4-FAIRNESS | При двух локальных требованиях и одном soft slot обе получают разумную очередь без starvation, ping-pong и повторного eviction в одном grace; перенос позиции/смена эпохи не создаёт stale claim | Любое повышение числа `materializeCalls` без фактического World outcome |
| P5-LOCAL | На native Giran footprint и контрастной зоне выполнены сохранение instance/3D и различие prewarm/couldKnow/World visibility; нет materialize всех профилей города без спроса | Простое расширение произвольного XY-radius и счётчик `candidate>0` |
| P6-SCHEDULE | 7×24×60 минут проверены детерминированно: при достаточном числе новых профилей 24h есть ACTIVE-когорты, отдельные типовые смены около12–16h, нет всеобщего сна; persistence/restart не сбрасывает assignment/epoch/goal | Реально ждать24 часа, переписать даты/состояние старых профилей или сделать все ACTIVE 24/7 |
| P7-LIVE | Финальная ordinary Main без test-controller действительно сохраняет own actors/earned items/EXP и sameDB restart; присутствие в World и полезные новые действия там, где input допускает; свежий PID/incarnation, actual retained resources | Synthetic injected network human; старые EXP/SP, savedXYZ или один stale World snapshot |
| P8-PRESERVE | Исходные frozen/affected M1/M2/M3 и relevant 064 регрессии зелёные; source build/JAR hashes закреплены, старые DB/JAR/profiles unchanged | Урезать IDs/failures, поломать visible party/support/commerce, «PASS» по unit-only |
| P9-DELIVERY | Один новый STOPPED candidate с верными SHA/DB/ports/config, точный report и acceptance065.tsv, normal push HEAD=remote; owner QA отдельно DEFERRED | Исходный dist/START без manifests или изменённые protected releases |

`CORE065=PASS` допустим, если P0–P9 подтверждены в их измеряемой серверной части. Если настоящую физическую сетевую сессию игрока не моделировали (владелец не входил), **`OWNER_CLIENT_QA_DEFERRED`**, не FAIL серверной проверки. Если в ordinary нет законного World demand, не принуждать его и не заявлять P7 по чужой TEST-сцене: status PARTIAL с точной причиной.

**Временные ограничения:** максимум6ч, финальный резерв1ч. Когда готовый действующий код соответствует критериям раньше — заканчивать раньше. Не превращать задачу в серию 10 отдельных многодневных campaigns.
