# Native farm continuation — Implementation Plan

> Исполнение: Codex inline, без субагентов; использовать executing-plans и test-driven-development. Не создавать отдельные диалоги для пунктов этого плана.

**Goal:** остановить потерю native продолжения и получить5полных последовательных циклов фарма.
**Architecture:** существующий Phantom adapter управляет intent/session; stock Mobius исполняет действия. Точные completion/registration/evidence не заменяются вторым combat engine.
**Tech Stack:** текущие JDK25, Ant, MariaDB. Новых зависимостей нет.
**Spec:** DESIGN.md; scope TASK.md/SOURCE_MAP.tsv.

## Global Constraints

PLAY только SELECT/export; guardedTEST только существующий gate/cleanup. Основной checkout/foreign diff не меняются. 120активных минут,15минут cleanup reserve;<=3semantic rounds,<=2connected episodes. Ни одного hot patch работающего runtime. Внутри authorised slice не останавливаться на первом соседнем subreason.

## Review Focus

- stale reason скрывает frozen invocation: S01/S02,3thread dumps до остановки;
- temporary checkpoint спутан с revocation: S03/S04;
- late callback/earned reward живёт дольше ticket: S07/S09/S14;
- phase deadline выдан за buffer overflow: S08/S10;
- shared pool зависает из-за одного actor: S06/S16.

## A. Суженная причинная проверка — до25минут

- [ ] Exact isolated base/worktree; отдельно прочитать root/ancestor AGENTS. Не только docs/AGENTS.
- [ ] Использовать ROOT_CAUSES и existing EVIDENCE021: seq7/age167199/CAST/REGEN — уже факты, не повторять166секунд ожидания.
- [ ] Retained021 logs/clone читать без изменений. Найти exact first native incident/stop/worker boundary. Три требуемых ответа: почему stopAutoPlay; почему не завершился cast/decision; почему sensor стал UNPROVEN.
- [ ] Классифицировать несколько причин как общий root либо независимые. В ROOT_CAUSE_PROOF.md — 1–2страницы с file/method/line и actual/fixture доказательством; неизвестное пометить UNKNOWN.
- [ ] Native composed fixture + нужные минимальные diagnostics; semantic RED на текущей базе. Стеки нужны при stall; не исправлять лишь последний reason string.

Если в25минут нет воспроизводимой причины, не делать догадочный semantic patch. Разрешён один observation-only диагностический committed build/episode022a с готовыми триггерами сбора; он расходует первый из2connected episodes. После него ещё можно выполнить confirmed fixes и022b в той же задаче. Новый ZIP для этого не нужен.

## B. Связанный ремонт — ориентир35минут

- [ ] Для каждого подтверждённого дефекта добавить RED по SCENARIOS. До3semantic rounds, не3runtime запуска.
- [ ] Внести минимальный repair на существующем producer/adapter. CONDITIONAL_CORE фиксировать в PATCH_LEDGER.tsv: sourcepath, method, RED evidence, зачем без этого не исправить farm.
- [ ] Перепроверить stale callback, policy replacement, paused/revoked, earned completion и other-actor control.
- [ ] Допустимо исправить2–3связанные границы в этих rounds; новый внутренний D-code сам по себе неSTOP.
- [ ] Native5cycle smoke с реальными callbacks; target/reward/loot truth. Если успешен только mocked start — до connected ещё неготово.

## C. Регрессия и артефакт — ориентир15минут

- [ ] Существующие TASK021 Run-Engineering команды использовать как образец classpath/UTF-8/guard; не перезапускать весь исторический framework.
- [ ] Mandatory regressions + затронутые native suites. Полный cleanup TEST и exact guard verification обязательны; потерянный journal не удалять вручную.
- [ ] Scope review + encoding/mojibake/escaped Cyrillic + diff --check. Test logs raw не подправлять.
- [ ] Exact-path source commit/normal push. Clean committed build, оба JAR SHA256, runtime binds только к этому SHA. Dirty compiled bin не деплоить.

## D. Controlled client proof — до2episodes по300секунд

RUNBOOK.md. Default один финальный вход; второй только если первый дал подтверждённый inside-scope root, после logout/stop, patch/regression/new committed build. Не анализировать код пять минут посреди активного окна вместо сбора samples. Один automation process собирает baseline/poll/cohort и trigger dumps.

## E. Итог и safe exit — резерв15минут

- [ ] Дождаться «вышел»; verify exact TestAdmin online0/level/exp/sp/xyz; stop run/consent.
- [ ] Снять final native owner/registration/drain состояние ДО закрытия JVM; verify selected Phantom rewards/store, preserve all failures.
- [ ] Graceful owned stop; stock processes exited не равняется Phantom drain complete.
- [ ] RESULT/HANDOFF/PATCH_LEDGER/evidence и normal push независимо от исхода.
- [ ] Один итоговый GREEN/BLOCKED/FAILED; остановка по TASK022/бюджету, не выдуманной просьбе пользователя.
