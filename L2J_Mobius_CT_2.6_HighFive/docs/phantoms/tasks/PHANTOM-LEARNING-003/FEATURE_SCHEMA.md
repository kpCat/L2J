# V3 — признаки и честная интерпретация боя

Никакого списка из «10 млн возможных боёв» в коде. Вход является набором entities и отношений, action proposal оценивается отдельно. Для поля всегда: source native/observed, нормализация, valid/unknown mask, schema fingerprint, bounded size, provenance, timestamp/epoch. Правило: UNKNOWN не есть ZERO.

## Self
`profile`, `object identity/epoch`, `classId`, `active/base class`, `level`, `learned skills/magic level` с проверкой native skill availability. Текущие/max HP, CP, MP; P.Atk/M.Atk/P.Def/M.Def; cast/attack/move speed, physical/magic critical, evasion, reflect; текущие оглушение, немота, рут и прочие abnormal states. Эффекты (id/level, duration/reuse, visible owner). Оружие: grade, family, enchant, SA/augmentation если реально доступно, shots; броня: сет, active effects; **бижутерия**: предметы и фактические M.Def/element/abnormal res; consumption/protection. Не превращать itemId в модельный «закон», но сохранять его как проверяемую категорию.

## Ally, Party, Summon
До 9 Party members, role, current/max HP/MP/CP, detected threat, relative position, last actions, known capabilities. Actor vs pet/servitor не смешивать, связь ownerId. NPC-pet HP, skills, UD and death/resummon readiness only if known. Избегать скрытого чтения чужих cooldown/inventory при PvP.

## Enemy/NPC
До bounded K ближайших/угрожающих противников; npcId/actual level, HP, type/resist/defensive values только если lawful query, actor target, recent witnessed skill/cast, distance, combat relation and line of sight, phase/status and observed buffs/debuffs. У human enemy только то, что клиент или legit AI может видеть; скрытый inventory, приватные шансы/готовность навыков, неподтверждённые намерения — UNKNOWN.

## Spatial
Distance 2D/3D, relative angle (вперед/сбоку/со спины), facing actor/enemy, height delta, Geo region availability, LOS, walkability evidence, reachable range flag from existing geo cache; никакого нового A* в каждом выборе. Дистанция относительно skill range и скорости сближения (если известна), продолжительность действия.

## Action
Тип: DAMAGE / HEAL / RECHARGE / RESURRECT_SKILL / CONTROL / DISPEL / MOVE / RETREAT / SUMMON / SPOIL / SWEEP / future. SkillId/level/effect, actual target relationship, MP/HP/items, cast/reuse, safe direction/range, stock owner and action generator identity. При отсутствии native proof `trainable=false` в данной версии.

## Критические сравнительные проверки
- Archer 61 и Mage 68+ при сопоставимых правилах: изменение magic level, навыков и grade меняет факты и оценку опасности; нельзя hardcode исход боя, сравнение только по реальной игре.
- С одинаковым classId, но разными jewels / magic resist / buff set вход различается.
- При том же уровне и gear изменение фланг/спина/дистанция меняет spatial features, не абстрактную метку `range=1`.
- Summoner + Cat представляются двумя отдельными субъектами с owner relation; heal cat и heal self различимы.
- Cardinal с двумя уязвимыми союзниками получает две разные реальные лечебные альтернативы; safety-critical выбор никогда не затирается random.
- Разные классы с тем же skill family могут переносить опыт через признаки действия, а не только skillId.

## Бюджет
Верхние bounds измерить и утвердить явно, например 1 self, <=9 allies, <=2 summons, <=8 enemies, <=16 legal actions (не обещать этих чисел без benchmark). Порядок/множество не должно зависеть от HashMap iteration. Для неприсутствующих единиц — mask; для недоступных API — UNKNOWN и read-only explanation. Все реальные значения normalise от игровых диапазонов с saturation warning, не превращать уровень 85 и 61 в одно и то же число.
