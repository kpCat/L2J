# TEST_MATRIX011 — RED→GREEN и настоящая проверка

| ID | Негативный / положительный тест | Требуемый смысл |
|---|---|---|
| S01 | Полный source catalog с персонажами разных классов и inherited/enchant learned skills | Не использовать hardcoded class→skill |
| S02 | Stock skill есть в learned state, но недоступен на target из-за magicLevel/MP/cooldown | Refuse с конкретной причиной, не засчитывать как alternative |
| S03 | Две разных lawful alternatives для настоящего target; остальные навыки в inventory не теряются | Shortlist ≠ full inventory; bounded rotation |
| S04 | Нет подходящего уровня NPC/reward или helper Party | Объяснимое `NO_SUITABLE_*`, отказ до GS |
| S05 | Headless config OFF/active, EnchantItemHPBonusData и реальное equipment/HP | Честный DIFF/проверенный parity, не молчаливое копирование HP |
| S06 | В окне180с actual CombatRequest ограничен120с / Party60с | Headless отказ до GS через canonical constructor |
| S07 | NPC меняет position/faction/AI во время подготовки | Устаревший proposal не seal; bounded replan или отказ до CUT |
| S08 | Faction neighbor появляется из другой World region | Не упускать опасного соседа при boundary crossing |
| S09 | NPC намеренно не имеет isolated legal point | НЕ добавлять произвольную точку/опасную зону; конструктивная причина |
| S10 | Разные native risk/profiles / expired owner | Matched physical seal отказ или безопасный подбор *до CUT* |
| S11 | Пара с одинаковыми pre-CUT факторами, отдельными incarnations/event keys | AB и BA воспроизводимы и различимы |
| S12 | Получены only partial arms/timeout/raw effects | Это не completed pair и не учебный gradient |
| S13 | Продолжение после Stop/Resume | Reserved episodes не переигрывать; COMMIT/ledger immutable |
| S14 | Все модели и SQL read-only EVAL | DRAFT14, Adam903/223, apps48, pointer12 byte-identical |
| S15 | Нет ни одной lawful сцены | Нет запуска GS и ложно положительного gate |
| S16 | Native revalidate отличен от headless | Счётчики/parity diff/negative receipt, не принудительная нормализация |
| S17 | Слишком долгий planning, отмена/пауза | Bounded work и cooperative cancellation, no JVM hang |
| S18 | Незнакомый класс/подкласс в acquired input | Inventory discovers, supported action family eligibility честна |

## Существующие регрессии

- `phantom-learning007-test`, `phantom-learning008-test`, существующий autonomous009 focused target, 010 preflight/stop tests;
- `ant -f L2J_Mobius_CT_2.6_HighFive/build.xml jar` после focused тестов, отдельно и в правильном cwd;
- native SQL/ports/owner guard, PowerShell config/parser — только при реальной затронутой подсистеме;
- `git diff --check`, mojibake-marker и escaped Cyrillic checks с exit codes.

**Native011**: `>=2` complete matched Solo pairs и `>=2` complete matched Party pairs с обеими очередностями AB/BA, достоверные CUT→paid→terminal receipts и никакого training credit. Пары из007/008/010 не входят в numerators011. Варианты Party HEAL/DAMAGE должны проходить обычный Party/Combat owner; не обещать специальный RAID или PvP.

Оценка игрового преимущества на четырех парах почти наверняка INCONCLUSIVE; технический gate — реальность сцены и приемки, не «нейросеть сильнее stock».
