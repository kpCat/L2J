# ACCEPTANCE — Task 001A review closure

## A. Git provenance

- [ ] Branch `feature/phantom-world`.
- [ ] Actual starting HEAD зафиксирован.
- [ ] Remote ref зафиксирован.
- [ ] Original Codex commit `e7dcf575...` существует.
- [ ] Parent `16d61833...` подтверждён.
- [ ] User-amended commit `cdca7a3d...` существует.
- [ ] User amend не приписан Codex.
- [ ] Drift после package preparation отсутствует либо полностью объяснён.
- [ ] Task 002 не начата.
- [ ] Новый commit, не amend.
- [ ] Force push отсутствует.

## B. Scope

- [ ] Изменения только в exact Task 001A allowlist.
- [ ] Только High Five module.
- [ ] Нет production `.java`.
- [ ] Нет `build.xml`.
- [ ] Master plan не изменён.
- [ ] ADR 0001 не изменён.
- [ ] Task 001 audit artifacts не изменены.
- [ ] `verify-task-001.ps1` не изменён.
- [ ] Нет runtime config/data.
- [ ] Нет SQL.
- [ ] Нет binaries/build/log.
- [ ] Нет Task 002 files.

## C. Agents.md

- [ ] Нет старой двусмысленной pathfinding-фразы.
- [ ] Geodata absent зафиксировано.
- [ ] `PathFinding = 2` зафиксировано.
- [ ] Полноценный pathfinding без region files не считается рабочим.
- [ ] Runtime fallback назван непроверенным.
- [ ] Task 009 gate указан.
- [ ] Fake/null-network `GameClient` отвергнут.
- [ ] Outbound/session seam указан.
- [ ] Headless sink делает zero network I/O.
- [ ] `ServerPacket.runImpl(Player)` exactly once.
- [ ] Client packets не являются Phantom API.
- [ ] ADR 0001 остаётся `Proposed`.
- [ ] Несвязанные разделы не переписаны массово.

## D. Task 001 report

- [ ] Удалено `required after commit`.
- [ ] Удалено `to be recorded after commit`.
- [ ] Pre-commit verifier `43/43`.
- [ ] Final run 1 `43/43`.
- [ ] Final run 2 `43/43`.
- [ ] Final outputs identical.
- [ ] Результаты привязаны к `e7dcf575...`.
- [ ] Original Codex commit указан.
- [ ] User-amended commit указан.
- [ ] `Agents.md` amend назван пользовательским.
- [ ] Independent review section добавлен.
- [ ] Original content verdict `ACCEPT`.
- [ ] Amended branch verdict `ACCEPT WITH FOLLOW-UP`.
- [ ] P0/P1 отсутствуют.
- [ ] Next step — независимое review Task 001A до Task 002.

## E. Review record

- [ ] Review file создан.
- [ ] Scope reviewed.
- [ ] Git provenance.
- [ ] Findings.
- [ ] Architectural verdict.
- [ ] Follow-ups.
- [ ] Closure implementation.
- [ ] Current gate.
- [ ] Original task content `ACCEPT`.
- [ ] Amended branch `ACCEPT WITH FOLLOW-UP`.
- [ ] Task 001A `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- [ ] Task 002 `NOT_STARTED`.
- [ ] Codex не самопровозглашает независимый `ACCEPT`.

## F. Verifier

- [ ] `verify-task-001a.ps1` существует.
- [ ] Работает из любого repo subdirectory.
- [ ] Не использует DB/network.
- [ ] Не изменяет repo.
- [ ] Проверяет commit objects.
- [ ] Проверяет exact allowlist.
- [ ] Проверяет prohibited paths.
- [ ] Проверяет Agents content.
- [ ] Проверяет report provenance.
- [ ] Проверяет review states.
- [ ] Stable ordinal output.
- [ ] Exit `0` только при полном успехе.
- [ ] Pre-commit PASS.
- [ ] Final run 1 PASS.
- [ ] Final run 2 PASS.
- [ ] Два final outputs идентичны.

## G. Text quality

- [ ] UTF-8.
- [ ] Mojibake markers: 0.
- [ ] Escaped Cyrillic: 0.
- [ ] Нет placeholders.
- [ ] Нет ложных утверждений.
- [ ] Нет несвязанных переписываний.

## H. Report/push

- [ ] `docs/phantoms/reports/001a-review-closure.md` создан.
- [ ] Status честный.
- [ ] Changed files перечислены.
- [ ] Commands/results записаны.
- [ ] Production/DB safety записаны.
- [ ] Parent указан.
- [ ] Commit создан.
- [ ] Push успешен.
- [ ] Remote ref совпадает.
- [ ] Working tree clean либо pre-existing state объяснён.
- [ ] Manual gate `IMPLEMENTED_PENDING_INDEPENDENT_REVIEW`.
- [ ] Task 002 `NOT_STARTED`.
