# PHANTOM-EARNED-GEAR-077 — M8 earned gear

Дата: 24.10.2026. Статус: **PARTIAL; IMPLEMENTED_PENDING_OWNER_QA**. Выполнена только 077.
Native proof: **NONE**. DB, Main, клиент, новые candidates и soak не запускались.
Полный earned маршрут, развитие 1→85 и получение S80 не объявляются доказанными.

## Baseline и scope

Единственный checkout — текущий Git root; ветка `feature/phantom-world`.
До правок local HEAD, origin tracking HEAD и live remote HEAD совпали:
`81c8c2fb79afa4202f2dcebea8fd258879fa861d`. Tracked status был чистым.
Первый network-запрос был отклонён sandbox; повтор с разрешённым network-доступом прошёл.
Чужие untracked каталоги и все task packages оставлены вне commit; manifest 077 совпал 7/7.
Новых веток/worktrees, reset, clean, stash, force, изменений master нет.

Exact changed files относительно модуля `L2J_Mobius_CT_2.6_HighFive/`:

1. `java/org/l2jmobius/gameserver/phantoms/progression/PhantomNativeEquipmentPolicy.java`
2. `java/org/l2jmobius/gameserver/model/item/Weapon.java`
3. `test/java/org/l2jmobius/tests/phantoms/PhantomEquipment068PolicySuite.java`
4. `docs/phantoms/reports/PHANTOM-EARNED-GEAR-077.md`

## Read-first: поддержано / причина / не доказано

| Путь | Уже поддержано | Узкая причина / итог 077 | Не доказано |
|---|---|---|---|
| Native equipment policy | N/D/C/B/A/S/S80/S84; native expertise, passive masks, armor sets | Убран безусловный выигрыш grade; текущие actual Item защищены | Все классы и native Player transitions |
| SupplyJourney | Owned inventory, NPC BUY, подготовка shots/ammo, resume farm root, shortage/retry | Owner переиспользован без изменений | Полная paid→equip→shot→earned farm цепочка |
| OfflineSupply | Только опубликованный физический SELL stock; native quote/receipt | Переиспользует исправленный upgrade guard | Живая доступность high-grade stock |
| ProgressionService/backend | ActionLease/operation ownership, actual owned object, native useEquippableItem | Не изменён; direct setEquipped не добавлялся | Native equip/restart в игре |
| Acquisition planner | Native drop/spoil facts; recipe preparation отдельно planning-only | Не превращён в универсальный gear sourcing engine | Автономный gear drop/spoil/craft/warehouse маршрут |

Прочитаны все 8 файлов пакета, root/module AGENTS, README, workflow/efficiency/current status,
bounded master-plan/task-package разделы и 9 точных SOURCE_MAP code/test срезов, `build.xml`.
AGENTS выше Git root и в docs/phantoms/tasks не найдены; повторный поиск не выполнялся.
Локальные аналоги: equipmentAffinity projection; shortageOffer; ProgressionService→native equip;
Supply071 binding/codec/receipt contracts. Нового commerce/AI/economy owner нет.
Дополнительный read-set: Item, ItemTemplate, Weapon, EtcItem, ArmorType, FuncTemplate,
ConditionUsingItemType, Inventory, L2jProgressionBackend; OwnerConfig build hook;
PhantomHeadlessPlayerSuite/Fixture/TestEnvironment и ProgressionServerIntegrationSuite;
CommerceCatalog/Loader/Suite, AutonomousSupplyPolicy; `.gitattributes`; три XML образца.
Причины расширения: подтвердить API/hidden trigger holders, displacement, writer boundary,
отсечь DB-зависимый fixture и проверить build side effects. READ_SET превысил рекомендованный
лимит дополнительных файлов; большие исходники читались срезами, исторические отчёты не перечитывались.
Repository searches: 9 rg-вызовов, включая inventory инструкций; дополнительный поиск — exact symbols.

## Три связанных дефекта

1. Текущий weapon сравнивался как ItemTemplate: enchant/augmentation/attributes/special effects
   фактически оценивались в ноль. Теперь guard получает actual equipped Item; enhanced,
   quest/hero/PvP/skill-bearing gear сохраняется консервативно, без пересчёта native статов.
   `Weapon.hasTriggeredSkills()` — единственный дополнительный native symbol: read-only наличие
   закрытых onmagic/oncrit holders, без resolution/cast/новой механики.
2. Более высокий grade обходил utility. Теперь необходим строгий рост конечной utility и legal grade;
   full armor сравнивается с суммой заменяемых chest+legs. Освоенная специализация и завершённый
   armor set не отбрасываются ради неподтверждённого улучшения. NaN/unknown utility fail-closed.
3. Ревью обнаружило native LR_HAND displacement: новый staff мог снять защищённый LHAND shield.
   Weapon guard включает offhand даже при пустом RHAND. Shield/unknown offhand сохраняются;
   ARROW/BOLT допускаются к штатному снятию и не считаются grade текущего weapon.

Ограничение: любые enhanced/special текущие вещи блокируют автоматическую замену, включая
потенциально выгодную. Это сознательный fail-closed, не полноценный optimizer enchant/SA.
Улучшенный owned candidate того же template отдельно по усилениям не ранжируется.

Writer audit: новые runtime/durable writers отсутствуют; изменены только read-only preference
и presence getter. Supply `_active`, materialization ActionLease, progression operation ownership,
goal binding и canonical commerce receipt path не заменялись. Native equip остаётся в backend;
нового monitor/atomicity обещания нет. Полный native concurrent writer/restart proof не запускался.
OwnerConfig, 38 rules, +25/Servitor Share, 075/076, quests, конфиги и сторонние runtime не редактировались.
Read-only OwnerConfig Verify: exit 0, 68 files, 14 overrides, changed=0; оба Ant Apply: changed=0.

## Источники и стоимость

XML inventory по точному predicate `Weapon/Armor ∩ buylist ∩ npc.type=Merchant`:

| Grade | Уникальные gear IDs | Native buylists |
|---|---:|---:|
| N | 133 | 117 |
| D | 336 | 155 |
| C | 280 | 50 |
| B/A/S/S80/S84 | 0 каждый | 0 каждый |

Это SOURCE inventory, не loaded runtime stock: spawn/access/tax/limited stock остаются native authority.
S/S80 не гарантированы NPC каталогом. Multisell, private manufacture, drop/spoil/recipes не объявляются
обычным NPC buylist. Нет бесплатных предметов, материалов, денег, shots, SP/MP или телепортов.
Supply choices уже учитывает native цену/налог, distance, outbound+return GK fees, reserve и mobility;
будущие charges оплачиваются с gear earmark. При отсутствии денег/stock — existing typed shortage,
finite retry и сохранённый farm root. NPC→offline fallback пока не является общим оптимизатором цен.
Owned warehouse discovery существует в progression API; автоматический gear withdrawal здесь не доказан.

## Проверки и gates

| Gate | Фактический результат |
|---|---|
| P0_NATIVE_GEAR | SOURCE_PASS + FOCUSED_PASS; IMPLEMENTED_PENDING_OWNER_QA |
| P1_EARNED_SOURCE | PARTIAL: lawful inventory/NPC/offline owners сохранены; high-grade earned supply не доказан |
| P2_CONTINUITY | FOCUSED_PASS existing receipt/binding/codec; IMPLEMENTED_PENDING_OWNER_QA |
| GRADE_COVERAGE | FOCUSED_PASS N–S84 level/expertise; availability отдельно ограничена таблицей |
| SAFETY | SOURCE_PASS: exact scope; новых DB/runtime/resources/writers нет |
| DELIVERY | Compiled/JAR/DB-free checks PASS; publication receipt формируется после commit/push |

Все логи и диагностические scripts: `.phantom-local/logs/PHANTOM-EARNED-GEAR-077/`.
`javac -encoding UTF-8 -cp <existing build/test/libs> -d <goal classes> <exact touched Java>`:
RED 2/4, затем 2/5 на старом extracted gate; GREEN 5/5. Один setup compile exit 1:
пропущен red-classes в classpath; исправлен только classpath, повторный RED был содержательным.
После review RED offhand 5/6, адресные policy/test javac exit 0, итог 6/6.
`ant -f L2J_Mobius_CT_2.6_HighFive/build.xml jar compile-tests`: exit 0, 49 секунд.
Отклонение бюджета: один Ant invocation выполнил compile дважды из-за двух top-level targets;
Ant JAR assembly один. Пять прежних test warnings: ExtendedOpenOption и System.runFinalization.
После review fix: один class-only `jar --update --file build/dist/libs/GameServer.jar -C build/bin
org/l2jmobius/gameserver/phantoms/progression/PhantomNativeEquipmentPolicy.class`, exit 0;
обычный Copy-Item в module/dist/libs. Повторного полного Ant/JAR не было.
`java ... PhantomTestLauncher equipment068-policy 770077`: финал **6/6**, exit 0;
`java ... PhantomTestLauncher supply071 770077`: финал **5/5**, exit 0.
Обе suite повторены непосредственно с final JAR в classpath: 6/6 и 5/5, exit 0.
`java ... PhantomTestLauncher negative 770077`: ожидаемый **exit 1**, intentional failure 1/1.
Policy runs: 7 (3 RED, 4 GREEN); supply runs: 3 GREEN; negative: 1; full verify: 0.
Projection fixtures включают physical sword/magic blunt, mastery masks, +25, skills, chest/legs,
shield и ammo. Они не исполняют `upgrade(Player,...)`, native equip, completed-set transitions
или earned actor и не выдаются за игровое доказательство. Native/client QA — DEFERRED_BY_OWNER.
XML inventory и artifact binding выполнены по два раза: byte-identical, exit 0.
JAR сравнен со всеми **4580** соответствующими compiled classes: mismatches=0; обе копии JAR равны.
Inline PS text-check не стартовал из-за parser quoting; заменён двумя отдельными Python regex scans.
mojibake-маркеры в изменённых файлах проверены; совпадений нет.
escaped Cyrillic в изменённых файлах проверены; совпадений нет.

## SHA-256 и публикация

Final GameServer.jar: `487d74c97c8bde8aeffc6c1a57c769405798e0fd560ceba0ff8076559aec9b47`.
Production Weapon.java: `a7529b6c36661bb9b9837371b5785a01ba7451e8cb84f7c652e3ec28ec99d314`.
Production policy.java: `e8aa6073548b6d08ca6750dd3a4602e49d4aede53d3d0d7fb4209bb41c353f64`.
Policy suite: `bf1986e473ae07a77b77626115d82406d4ec7a8c26ff962ca473435bdb01b071`.
Baseline JAR: `10269119e1f1bc4b96daf72653fec65dfd25ed70731bd35af55879e6a449565a`.

Git разрешён текущим TASK/DELIVERY и явным запросом пользователя. Выполняемые команды:
`git symbolic-ref --short HEAD`; `git rev-parse HEAD`;
`git rev-parse refs/remotes/origin/feature/phantom-world`;
`git ls-remote origin refs/heads/feature/phantom-world`; `git status --short`;
`git diff -- <три exact code paths allowlist выше>`; `git diff --stat -- <те же paths>`;
`git diff --check -- <те же paths>` дал CR-at-EOL в существующем CRLF Weapon.java;
`git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --check -- <allowlist>`
сохраняет обычные whitespace checks и разрешает исходный native CRLF, без изменения Git config.
Финальные `git add -- <четыре exact paths выше>`, scoped `git diff --cached --check`,
`git diff --cached --name-only`, `git diff --cached -- <allowlist>` проверяют exact staged scope;
`git commit -m "phantom(task-077): retain effective earned equipment during upgrades"`;
`git push origin HEAD:refs/heads/feature/phantom-world` — normal non-force;
`git status --porcelain --untracked-files=no`, local/tracking/live HEAD проверяются после push.
Точный итоговый commit SHA и post-push status пишутся в `delivery.log` и финальный ответ:
самореферентный SHA не встраивается в единственный публикуемый commit отчёта.
Telemetry на report checkpoint: около 20 минут, Goal usage 232326 tokens; budget не задавался.
Отдельный read-only reviewer проверил финальную правку: оставшихся Important/Critical нет.
После публикации остановка; 078/R2 не начинаются, автоматических follow-up нет.
