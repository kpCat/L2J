# F068 — голубые чемпионы

Состояние блока: реализация и focused proof выполнены; финальная поставка committed-source JAR остаётся общим gate основного исполнителя. Клиентское отображение цвета — DEFERRED_BY_OWNER.

## Реализация

Один `ChampionType`: NORMAL, RED или BLUE. Spawn сбрасывает прошлый tier/team и выбирает новый одним categorical roll до `spawnMe`, поэтому первый NPC_INFO уже содержит правильную команду. Сумма включённых итоговых вероятностей выше 100 отключает новый голубой блок с warning; красный остаётся доступным. Без Blue-ключей система остаётся red-only. Неподходящий NPC не расходует champion RNG. Ограничения monster/quest/undying/raid/minion/level/instance общие по смыслу, диапазоны и разрешение instances — отдельные для каждого типа.

Красные ключи и их числовые defaults сохранены. Штатный HP-контракт сохранён: входящий урон делится на ChampionHp; Max HP и его packet value не увеличиваются. Никакие множители не накапливаются в шаблоне: stats/rewards читают только один tier. Новые ChampionSpoilChance/Amount по умолчанию 1.0, потому что stock red не умножал spoil table. Старый extra reward путь на DROP/SPOIL сохранён.

BlueChampionEnable по умолчанию False; частота 2%, эффективный HP 2×, EXP/SP 2× и ordinary drop chance 2× слабее stock red. Все BlueChampion-ключи находятся в ChampionMonsters.ini: passive, title, aura, уровни, HP, regen, attack, attack speed, EXP/SP, drop chance/amount, Adena chance/amount, отдельные Seal Stones chance/amount, spoil chance/amount, extra items и их lower/higher chances, vitality, instances. Ключи и комментарии не требуют правки Java для настройки.

Для красного сохраняется фактическое старое поведение per-item server rate overrides: champion множитель применяется там только к Adena. Для голубого явные ordinary/Adena/Seal Stones множители работают также при per-item override. Herb/raid/premium/native level-gap правила не заменялись. EXP/SP изменены в обоих существующих solo/party reward consumers; regeneration/stats/vitality/quest rewards/admin kill/title используют выбранный профиль.

## Файлы этого исполнителя

Все пути ниже относятся к L2J_Mobius_CT_2.6_HighFive.

- dist/game/config/Custom/ChampionMonsters.ini
- java/org/l2jmobius/gameserver/config/custom/ChampionMonstersConfig.java
- java/org/l2jmobius/gameserver/config/custom/ChampionSelectionPolicy.java — новый
- java/org/l2jmobius/gameserver/model/actor/enums/npc/ChampionType.java — новый
- java/org/l2jmobius/gameserver/model/spawns/Spawn.java
- java/org/l2jmobius/gameserver/model/actor/Attackable.java
- java/org/l2jmobius/gameserver/model/actor/Creature.java
- java/org/l2jmobius/gameserver/model/actor/stat/CreatureStat.java
- java/org/l2jmobius/gameserver/model/stats/Formulas.java
- java/org/l2jmobius/gameserver/ai/AttackableAI.java
- java/org/l2jmobius/gameserver/model/actor/templates/NpcTemplate.java
- java/org/l2jmobius/gameserver/model/script/Quest.java
- java/org/l2jmobius/gameserver/network/serverpackets/AbstractNpcInfo.java
- dist/game/data/scripts/handlers/chat/commands/admin/AdminMenu.java
- dist/game/data/scripts/handlers/chat/commands/admin/AdminKill.java
- test/java/org/l2jmobius/tests/phantoms/ChampionMonsters068PolicySuite.java — новый
- test/java/org/l2jmobius/tests/phantoms/ChampionMonsters068Suite.java — новый
- docs/phantoms/tasks/PHANTOM-LIVING-ECONOMY-068/F_HANDOFF068.md — новый

Bounded exception для более 10 связанных файлов разрешён AUTHORITY068: список расширен ровно на найденные существующие consumers одного champion-контракта. Player/GameServer/Shutdown/build.xml/launcher этим исполнителем не изменялись. Основной исполнитель добавил mode champion068-policy и champion068 в общий launcher.

## Проверки и реальные результаты

JDK25. Seed 20260725001. Все native команды работали из собственного `.phantom-local/task068/native-test/game`, с `phantom.task068.native=true`, source module root и exact Database.ini этой среды. Использовался только guarded `l2jmobiush5_phantom_test_068`; внешние серверы и исторические БД не запускались. Bootstrap вызван infrastructure-only, без массового fixture initialize/cleanup. После каждого native JVM выполнены ThreadPool.shutdown и DatabaseFactory.close; процессы завершились обычным exit.

1. Isolated javac 12 production Java + 2 suites + launcher against baseline067: exit 1, только ошибочное предположение теста о readString в ArrayPacketBuffer. Исправлено чтением через реальный readChar. Это setup failure, не product RED.
2. Основной исполнитель выполнил integrated Ant jar + compile-tests: PASS, 2346 production/310 test source, около 28 сек. Сборка `.phantom-local/task068/build-dev`.
3. Launcher `champion068-policy 20260725001`: exit 0, PASS 2/2. 25 пар частот × 100 buckets дают точные итоговые числа RED/BLUE; проверены границы, 0/100, сумма выше 100 и eligibility fences. Отчёт `.phantom-local/task068/reports/champion-policy/champion068-policy.txt`.
4. Первый launcher `champion068`: exit 1, aura/reset и все tier rows выполнены; последняя assertion сравнила Integer 450 и Double 450.0 как Objects. Исправлено сравнение численного расстояния. Это test defect, не найденный product RED.
5. Isolated javac текущих config + native suite в `.phantom-local/task068/champion-bin`: exit 0; вывод compiler пустой.
6. Launcher `champion068 20260725001` с champion-bin перед build-dev: exit 0, PASS 2/2, native assertions около 0.17 сек после примерно 12 сек native initialization. Отчёт `.phantom-local/task068/reports/champion-native-green/champion068.txt`:

| Tier | Урон при raw 80 | DROP при base 10 | SPOIL при base 10 | Настоящий NPC_INFO team | Title |
|---|---:|---:|---:|---:|---|
| NORMAL | 80 | 10 | 10 | 0 | пустой |
| RED | 10 | 10 | 10 | 2 | Champion |
| BLUE | 40 | 30 | 40 | 1 | Blue068 |

Blue068 — заранее объявленный component TEST profile с отличающимися quantity multipliers; frequencies для каждого отдельного spawn input заранее forced 0/100. Затем выполнен same-object BLUE→NORMAL respawn: team 0, native HP восстановлен полностью. Отдельно проверен red aura reset после выключения aura. NPC_INFO сериализован production writeImpl в штатный ArrayPacketBuffer и прочитан без GameClient. Это серверное packet evidence; живой клиент не использовался.

7. Cheap отдельный `Champion068BaselineAura` с production JAR baseline067 и только bootstrap068 guard: exit 1, достоверный product RED `Expected NONE but was RED`. Тот же публичный setChampion(true) → aura OFF → setChampion(false) проходит в current native suite. Локальный disposable baseline source и bin остаются в ignored `.phantom-local/task068`; они не являются поставляемым test framework. Лог `.phantom-local/task068/reports/champion-baseline-red.log`.
8. Read-only `git diff -- <exact F paths>` использован для self-review. Обычный bounded `git diff --check -- <exact F tracked paths>` вернул exit 1: CRLF считался trailing whitespace. Повтор с `git -c core.whitespace=cr-at-eol diff --check -- <тот же exact список>` — exit 0. Исходные CRLF не переписывались массово. Git write/commit/push не выполнялись.

## Оставшиеся ограничения и интеграция

Последняя маленькая config-правка после build-dev безопасно сохраняет red spoil 1× при некорректном новом red multiplier и не расходует RNG для полностью неподходящих NPC. Требуется обычная финальная общая пересборка, затем короткий повтор champion068-policy/champion068 на конечном JAR без isolated override. Доставку и commit/source SHA фиксирует основной исполнитель.

Native fixture проверяет material DROP/SPOIL и HP/packet/reset, но не объявляет измеренной отдельную статистическую частоту наград, player kill EXP/SP, каждую валюту, premium/vitality и каждый admin script. Соответствующие реальные production consumers прочитаны/изменены адресно; итоговые spawn probabilities доказаны DB-free исчерпывающими buckets. Полный клиентский rendering остаётся DEFERRED_BY_OWNER.

Mojibake-маркеры в изменённых файлах проверены отдельной полной проверкой.

Escaped Cyrillic в изменённых файлах проверены отдельной полной проверкой.
