# Когда M1 действительно закрыт

## Уровень 1 — ENGINEERING_GREEN

Q/P/E/T/L/A/W из SCENARIOS выполнены на actual source, без обязательных NOT_RUN.
Unknown producer coverage, необработанный Critical/Important, invalid fixture RED,
failed combined suite или неготовый exact build запрещают READY.

`b4f1f03…` и его protections сохранены. Candidate006 больше не является лишь patch:
принятый source опубликован, codeSHA известен, clean JAR и зависимости связаны с SHA.
RESTORE/fixture cleanup выполнены без ручного PLAY изменения. Existing real/offline
players и synthetic identity positive controls проходят.

Это только разрешимость controlled deploy. M1 ещё OPEN.

## Уровень 2 — два synthetic GREEN на одном принятом коде

New materialization, contact, native life, cohort, soft return, restore/stop доказаны
в каждой независимой scene. Второй selected profile отличен от первого; выбор естественный
из заранее объявленных eligible candidates, без ручного исправления персонажа.
No producer fix/deploy между этими двумя GREEN. Старые synthetic runs и №5 не зачтены.

Native life: selected actor завершил минимум 2 последовательных полезных farm cycles
за bounded OBSERVE window (до 180 s): его native damage, соответствующий kill/reward,
положительный EXP/SP где это штатно возможно, выбор следующей цели. Лут доказывается
native pickup/autoloot evidence при наличии eligible reward. Отсутствие выпавшего item
не объявлять ни loot failure, ни loot PASS; ground pickup уже обязан быть доказан native TEST.

Cohort: не менее 4 natural eligible; selected и ещё как минимум 2 участника имеют
полезный attributed native progress. Остальные показаны поимённо с жизнеспособной
ограниченной фазой (route/combat/regen/death recovery), не скрыты фильтрами.
Необъяснимое отсутствие полезного/ограниченного recovery progress 90 s — RED.
При реально большом native action/travel horizon использовать существующую проверенную
policy с уменьшением оставшегося budget, а не бесконечное продление срока.

Не допускаются: unexplained permanent idle, global self-heal loop, crowd в одной
непроходимой точке, массовый неверный уход в воду, perpetual Journey reset,
FAILED/admission closed без разрешённого recovery, row loss, unproven ownership.

## Уровень 3 — один настоящий connected client gate

Server-side exact TestAdmin REAL_LOGIN; fresh arm принят; runActive=false до запуска.
Ровно один connected M1 run, после которого UI Off и server-side OFF/no active run.
UiInGame/InputSentUnverified не достаточны. Один connected RED → точная boundary;
никакого второго run без исправления/независимого решения.

Обязательные grade имена сохранить:
NEW_MATERIALIZATION / CONTACT / NATIVE_LIFE / COHORT / SOFT_RETURN / RESTORE/STOP.
Дополнительно отдельные evidence outcomes: LOOT_PIPELINE / DEATH_RECOVERY /
NATIVE_NAVIGATION_WATER / PERSISTENCE_RESTART / ORDINARY_PLAYER_REGRESSION.
Они могут быть подтверждены engineering native TEST, если конкретная случайная scene
не породила смерть/loot/crash. Нельзя писать «видели в клиенте» о TEST-only событии.

Визуальное evidence: реальные frames/screenshots клиента на approach/contact/observe/
return с временем/actor, без постановочного teleport Phantom. Server-side факт не
заменяет visual proof natural continuity. Видимая пользователем повторяющаяся толпа,
неверное утопление/стояние при зеленых counters — conflicting evidence, M1 не закрыт.
Тестовый runner не способен математически гарантировать отсутствие любого бага во всех
локациях; финальный отчёт честно перечисляет coverage и не переносит известный M1 blocker в M10.

## Финальный verdict

M1_CLOSED только когда уровни1–3 PASS, без открытых блокирующих наблюдений, все personas/
режимы в declared coverage проверены, no active run/consent, artifacts опубликованы.
Иначе M1_OPEN + точный status/следующий необходимый шаг. Не заменять product verdict
словами build green, ready, diagnostics green или review none.

После M1_CLOSED — отдельная новая задача M2 Real Player Companion; не начинать её в этом пакете.
