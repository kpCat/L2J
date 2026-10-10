# TASK032 — план реализации

> Исполнение самим Codex по executing-plans/test-driven-development, без субагентов.
> Пользователь одобряет пакет отправкой /goal. Не спрашивать разрешения между этапами.

**Goal:** непрерывная native-группа с завершённым recovery и попыткой полного server closeout.
**Architecture:** existing Decision/History/Native executor, без второй системы управления.
**Tech stack:** Java25/Ant/MariaDB/PowerShell, один HighFive.
**Spec:** DESIGN.md; полномочия TASK.md; точные paths SOURCE_MAP.tsv.

## Review focus
- Законный cooperative damage нельзя запретить ради Gremlin (S08).
- Guard release не даёт права stale/foreign epoch (S04–S06,S09).
- Reader/JDI не меняет game timings/SQL и не генерирует late exact evidence (S01,S11,S13).
- Cooldown+rest+retry не превращаются в вечное ожидание (S02–S07).
- Новый deterministic test не скрывает death/cold/unavailable actor (S10,S12,S15).

## 0. Read-first и зафиксированная база (ориентир0–25мин)
- [ ] Проверить branch/base/source tree и owned stopped state; read-only old031 handoff.
- [ ] Не запускать обзор всех задач/всего репозитория. Прочитать пять методов PATCH_GUIDE.
- [ ] Copy exact needed private evidence43/117/C28 references; зафиксировать ограничения.
- [ ] Создать ownworktree/clone, native preflight. Проверить archive/task guardpaths до boot.
- [ ] Зафиксировать прогресс в одном WORK_LOG; никакого отчёта на каждый shell-вызов.

## 1. Первое решение43 и debt117 (25–90мин, firstserver до60)
- [ ] В existing raw найти первый useful-progress gap117, точные source/epoch timestamps.
- [ ] Разделить firstFalse43 на branches без изменения admission и без повторного SQL.
- [ ] Targeted probe; upstream recordVisibleTravelFailure/episode/reload связывать с firstFalse.
- [ ] Если exact43 отсутствует, проверить текущий естественный эквивалент; не переписывать
  runtime события прошлого. Triage всех исходных8 не заменяет окончательную cohort.
- [ ] ROOT_CAUSE_PROOF: факт/гипотеза/первый writer/потребитель/RED сценарий.

## 2. Реализация законченного перехода (90–240мин — ориентир)
- [ ] RED на установленный producer, composed native fixture. INVALID fixture отдельно.
- [ ] Минимальный fix по ветке DESIGN, включая связанный callback/goal exit если нужен.
- [ ] GREEN + парные negative stale/CAS/cooperative tests. Проверить реальную загрузку Jar.
- [ ] Короткий server continuation без полного93 sweep. Записать прежний и новый progress.
- [ ] Отдельная cause117, если не совпадает. Исправить, не открывая вторую архитектуру.
- [ ] Commit+normalpush bounded checkpoint. Исполнитель вправе сделать несколько связанных
  fixes; число patches не является STOP. Два опровержения одной гипотезы → новый анализ,
  а не поверхностный третий обход. Отсутствие нового факта60мин — checkpoint/смена метода.

## 3. Lineage и setup reliability до финальной заморозки
- [ ] Разобрать existing C28 read-only по LINEAGE. Сравнить exact payload, не только version.
- [ ] Runtime проверить последнюю task-only ReadArrivalFrame031 correction один раз.
- [ ] Подготовить один current away route и допустимый time budget. Не дробить поиск на
  десятки перезапусков GameServer: геометрию проверять существующим read-only helper.
- [ ] Незакрытая старая lineage не повод массово менять transactions. Новый trace обязателен
  при недостающем звене. Все gaps остаются OPEN до доказательства.

## 4. Frozen acceptance (стремиться начать до300мин; semantic cutoff360)
- [ ] Review exact diff, commit, чистая сборка sourceSHA, actual runtime Jar hash.
- [ ] Один целевой93+11 regression sweep на этой реализации; изменённые suites включены.
- [ ] StationaryA/B360–420s на одномcandidate, весь denominator; verdict legacy отдельно.
- [ ] Полный away/background/return, затем реальный farm вернувшейся группы.
- [ ] Whole-group save + два actual sameDB restart; lineage rules без ослабления.
- [ ] Crash72/73 currentcandidate на своих clone и здоровые recovery/shutdown.
- [ ] Не складывать PASS из разныхJars. Любой semantic fix после failed gate создаёт новый
  candidate, затронутые/полныеfinal gates повторяются, если остаётся бюджет.
- [ ] После360мин новые semantic fixes запрещены. Можно закончить диагностику/acceptance,
  но не обещать проверить ещё один патч за оставшиеся минуты.

## 5. Финиш (не позже480мин; последние30мин резерв)
- [ ] Все owned JVM stopped, receipts/queue exported, raw immutable private.
- [ ] MATRIX_RESULT по каждому gate и оставшемуся root, current source/Jar и timestamps.
- [ ] Exact-path report commit + normalpush, equalityremote, чистый own worktree либо
  объяснённые только private/untracked outputs. Foreign untouched.
- [ ] Один RESULT/HANDOFF/MORNING. GREEN только полныйserver, M1 не CLOSED без клиента.

Дедлайны отдельных фаз — контроль прогресса, не автоматический STOP на первом пропуске.
480мин и safety-boundaries — жёсткие. Если закончен полезный bounded slice и дальше нет
доказанного безопасного шага, завершить раньше с честной boundary.
