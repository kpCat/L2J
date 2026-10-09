# Native demand031 implementation plan
Для исполнителя: inline executing-plans; без субагентов.
Goal: разделить завышенный setup predicate и реальный текущий native отказ.
Spec: DESIGN.md. Stack: JDK25/Ant/MariaDB3308/PowerShell, имеющийся Synthetic.

## 0–20мин: вход и минимальный RED
- [ ] Exact base/remote и собственный worktree; AGENTS читать один раз.
- [ ] Read SOURCE_EXCERPTS.md и TASK030 RESULT/census/Observe/observer.
- [ ] Сохранить один неизменяемый preboot export own clone и классификацию READY,
      DEAD, INCONSISTENT, UNKNOWN, schedule, cached error.
- [ ] RED на composed ecology/native fixture: валидный exact native-context handoff
      имеет до demand dueSnapshot.complete=false; старый setup отвергает его.
      После настоящего demand production может вернуть typed handoff.
      Не доказывать native успех подменой production predicate.

## 20–45мин: исправление setup + first-cause hook + build
- [ ] В task-only Observe031 убрать только farmAllowed/readinessComplete prefilter.
- [ ] Reuse Control/Prepare030 в одном адаптированном entrypoint/observer, без новой
      инфраструктуры журналов. Допустимы до3 PS entrypoint +1 observer+1 audit script.
- [ ] При отсутствии existing подробного first-error добавить observational records
      в разрешённых source-файлах. Сохранить status/exception/rollback semantics.
- [ ] Unit negative controls и clean committed jar перед actual probe.
- [ ] Exact-path commit + normal push code; старт только своего runtime.

## 45–80мин: один причинный probe
- [ ] Existing Synthetic, без пользователя. Записать identities/manifest/TTL.
- [ ] Один выбранный до исходов cluster; optional initial Synthetic teleport согласно
      DESIGN. Цели/мобы/Phantom/EXP/skills не трогать.
- [ ] Наблюдать90с либо до точного устойчивого current failure с достаточным evidence.
- [ ] Capture exact real request kind и first exception до generic mapping.
- [ ] Если natural native игра появилась, пассивно до ещё120с: fixed cohort,
      same-epoch cycles/EXP/SP. Это smoke, НЕ две final scenes и НЕ M1PASS.

## До105мин: один разрешённый повтор или причинный вывод
- [ ] Второй probe только если первый был setup-invalid либо данные доказали ошибку
      самого diagnostic bridge. Не ещё одна semantic production repair round.
- [ ] Доказательство current source error и baseline/data contamination отдельно.
- [ ] Если все сведения доступны из baseline и первого run — повтор не нужен.
- [ ] Итоговая decision table: известное / неизвестное / точный next scope / риски.

## 105–120мин: обязательное завершение
- [ ] Graceful stop, pending/retained/real-state check, exact owned PID absent.
- [ ] Проверить diff/encoding/секреты, обновить один RESULT/HANDOFF.
- [ ] Exact-path commit + normal push reports при любом исходе.
- [ ] Один финальный ответ. Автоматические «blocker подтверждён» запрещены.

Ни одна цифра deadline не даёт права объявить failed probe успешным. При safety
ошибке сохранить точный evidence и безопасно завершить раньше. Старые logs не менять.

## Более приоритетная последовательность debug-first
**0–15 мин:** прочитать `DEBUG_PLAYBOOK.md`, build.xml debug symbols, first guard
исходной clone; найти GameServer start path и способ включить loopback JDWP
только на own PID; если невозможно, идти jcmd/JFR без переписывания launcher.
**15–35 мин:** первый GameServer/Synthetic probe c invariant baseline,
сразу `VM.command_line` и два `Thread.print -l` плюс JFR60s; никакой сборки
для trace, кроме отсутствующего исходного GameServer.jar.
**35–70 мин:** получить по одному native-demand переходу для фиксированных
profiles; jdb/IDE conditional breakpoint или bounded first-cause hook при шуме.
Snapshot `first guard + cause or none`, причинная цепочка и источник.
**70–105 мин:** лишь если trace подтвердил ошибку диагностического setup,
компактно поправить task-only setup и повторить максимум один раз;
если найден semantic production defect — не пытаться исправить его вслепую.
**105–120 мин:** снять JFR, сохранить status, graceful stop, RESULT/HANDOFF,
exact-path commit+push. Измеренные остановки debugger исключить из timing proof.
При коллизии с прежней последовательностью PLAN.md действует этот раздел.
