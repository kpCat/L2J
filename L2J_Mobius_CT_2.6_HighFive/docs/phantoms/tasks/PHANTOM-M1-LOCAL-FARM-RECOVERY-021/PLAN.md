# Visible local-farm recovery — Implementation Plan

Goal: исправить связанный цикл current goal / terminal route / local replan и проверить
пять native farm cycles. Architecture: только existing Phantom adapters и native Mobius.
Tech stack: текущие JDK25/Ant/MariaDB; новые зависимости не нужны. Spec: DESIGN.md.
Исполнение: inline Codex, без субагентов; текущие TDD/debugging правила сохраняются.

## Review focus

Проверить: reload BUSY; чужой callback предыдущего epoch; CAS conflict после планирования;
cleanup между commit/publish; safe endpoint с водным промежуточным сегментом. Все пять
покрыты SCENARIOS. Никаких фоновых threads/IO под monitor DecisionEngine.

## A. Read-first и RED, без нового manual run

- [ ] Проверить HEAD/base и task scope. Прочитать сохранённые RESULT/EVIDENCE020.
- [ ] Из retained observe020 read-only взять exact current goal/catchup/profile110;
  старую28 брать только из реально сохранённого trace/log, если там есть полный spec.
  Если его нет — OLD_RUNTIME_SPEC_UNAVAILABLE, не выдумывать npc/anchor старой версии.
- [ ] Проверить реальный call path attach/reload/materialization по SOURCE_MAP. Описать
  доказанную load-order возможность без объявления недоказанного producer runtime-фактом.
- [ ] В существующей test environment воспроизвести runtime28/current29 и terminal
  same-goal повтор. Deterministic RED должен падать именно по drift/recovery, не harness.
- [ ] На native scene/геодате проверить фактическую текущую local цель/сегмент; это можно
  сделать в isolated native fixture без TestAdmin. Отсутствие геодаты не лечить её заменой.

## B. Один согласованный implementation patch

- [ ] Добавить pre-work goal synchronization и запрет stale movement.
- [ ] Сохранить terminal reason и точную привязку при переходе travel -> decision.
- [ ] Recovery intent выполнять на следующей pre-work boundary, не из handler.
- [ ] Переиспользовать atomic replacePlan, безопасный runtime reload и exact permit rebind.
- [ ] Добавить visible-local planner overload, exclusion/episode budget из DESIGN.
- [ ] Прогнать SCENARIOS; текущие travel/native handoff/admission tests не ослаблять.
- [ ] Diff review пяти production files и связанных call sites. Никаких broad cleanup.

## C. Проверки и артефакт

Использовать existing Ant compile-tests target и existing standalone main suites.
Новые suites тоже standalone main, без изменения PhantomTestLauncher/build.xml.
Пример classpath/запуска уже в task020/Run-Engineering.ps1 — копировать verified команды,
а не переоткрывать всю build инфраструктуру.

- [ ] New targeted suites GREEN.
- [ ] Task020 V01–V18; TASK018 H01–H14; ecology30/30; handoff6/6;
  decision-core36/36; decision-persistence23/23; affected existing travel tests.
- [ ] Одна проверка recorder/census без изменения диагностики и без PREPARE.
- [ ] Убедиться, что guarded TEST восстановлен точным штатным journal path.
- [ ] Exact-path source commit + normal push.
- [ ] Clean build из этого committed SHA, без постороннего diff; записать JAR hashes.

Прежний broad materialization22/23 — НЕ весь GREEN. NATIVE_WORK_SELF_DRAIN остаётся
открытым пунктом lifecycle, не исключать из общего M1 debt и не чинить его здесь.

## D. Один connected proof

RUNBOOK.md: fresh clone, health, manual login, census t0..5min, no client motion,
no Phantom control. Не делать pre-fix manual run: наблюдение020 уже есть.
В runtime никаких новых semantic changes. На новом outside-scope дефекте — отчёт/STOP.

## E. Закрытие задачи

- [ ] Logout/save/graceful stop.
- [ ] RESULT/HANDOFF и исходные sparse evidence; две census-точки не называть soak.
- [ ] Exact-path report commit + normal push; подтвердить remote SHA.
- [ ] Единожды закончить GREEN/BLOCKED/FAILED. Не повторять три blocked-turn подряд.
