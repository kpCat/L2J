# SCENARIOS036 — обязательное покрытие

| ID | Фактическая проверка | Ожидаемое поведение |
|---|---|---|
| E01 | diagnostics=true: настоящий doCast/launch reserve; short helper barrier outside native locks | submit/dequeue/start/exit связываются с точным ticket/epoch; EVENT trace сохранён; base diagnostic RED |
| E02 | Actual MagicUseTask с живым NPC, без paused VM; launch/hit/finalizer | native MP/HP и при kill EXP/SP из штатных тел; каждый принятый ticket завершается ровно раз |
| E03 | requestDematerialize между reserve и due реального cast-launch | EARNED не cancel; cleanup ожидает, no new roots; FINALIZE затем World/AutoSave/lease отсутствуют |
| E04 | Stop запрошен во время RUNNING native callback, без self-wait | callback + заработанные дети заканчиваются, control worker не ждёт самого себя |
| E05 | Stock abort/death/новый MagicUseTask делает старое тело no-op | current-task/epoch защита работает; старый wrapper учитывает завершение, не пишет в нового Player |
| E06 | Cast child после DRAINING и отдельно new-root попытка | законный transitive child принят, новый root отклонён; parent identity проверена |
| E07 | Нулевая задержка/быстрый callback до SUBMITTED и повторный cancel | no ticket/reference resurrection, once-only завершение, unknown submit не исполняется повторно |
| E08 | Настоящее исключение body/submit rejection в TEST | original cause не потерян; receipt/owner не освобождаются ложным GREEN; нет двойного урона/награды |
| E09 | Один owner fault + второй с уже опубликованным earned cast | записать timeline до pool stop; здоровый callback не теряется из-за ошибки учёта/нового waiter; aggregate TRUE failure сохранён |
| E10 | Существующий S05 с callback длиннее общего deadline | исходный single deadline, FAILED остаётся FAILED; повторный вызов не добавляет полный новый wait |
| E11 | Два managed caster'а законно атакуют одного NPC, обычный Player control | совместная атака не запрещена; поздний hit по мёртвой цели не выдумывает EXP/SP и не оставляет outstanding |
| E12 | После текущего freeze: native35 full252/no new slots и reload | inventory035 сохранён; raw payloads/rows не подменены |
| E13 | Одна live natural группа, не менее одного реального caster; departure/stock stop | повторные native cycles и exact cast timeline; сохранение earned + global typed COMPLETE/retained0 до pool stop |

Минимум два разных caster lifetime в deterministic native-проверках; не выводить общий контракт из ID27.
Нельзя подменять doCast вызовом лишь callSkill/onMagicHitTimer для E02/E03: это обход launch publisher.
Для существующих callback tests callSkill может оставаться контрольным, но не доказывает E02/E03.

Фикстуры:
- Переиспользовать actual native environment и `.Fixture` из Handoff, WorkerGate из dynamic-recipient
  только в TEST lane; barrier обязан освобождаться в finally и иметь собственный bounded timeout.
- Test-only schedulable latch должен стоять ДО захвата actor/status/owner/SQL monitor.
- Не изменять production delay/MP/rates/NPC HP ради проверки. В TEST можно создавать штатных
  NPC и изолированные fixtures через existing gate; реальная сцена использует только native population.
- Expected reward derives from exact штатного NPC/конфига/числа доставок; не подгонять под увиденное20.
- Q15 из035 сначала pinpoint по raw/registry и run на подходящей036t; релевантный семантический FAIL
  запрещает GREEN, unrelated/missing prerequisite отдельно поясняется, не объявляется PASS.
- Failed scene сохраняет всех enrolled; старый epoch27 не пересоздаётся SQL либо присваиванием полей.

E09 — проверка границы и failure isolation, НЕ лицензия лечить любой fail shared shutdown.
При safe отказе остаётся явно FAILED и сохранённая pending ownership; это отрицательный PASS теста,
а не healthy GLOBAL_DRAIN_PASS. Не суммировать эти два вида результата.
