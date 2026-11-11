# Недорогой тестовый маршрут, затем только целевой native proof

## MODEL / DB-free (сначала)
1. Новый serializer V3 round-trip: rows/unknown masks/weights/version/checksum. Backward V1 12 484/12 500-byte models intact, corrupted/stale V3 reject, fallback штатный.
2. Изменение уровня, оружия, бижи, enchant, skill level, buff/effect duration, target level/resist, дистанции и orientation даёт различные релевантные features (если значение законно известно). Пример Archer61 vs Mage68 с равным gear и сравнение при изменении grade/jewelry — no hardcoded victory.
3. Variable Party <=9 / summon owner plus 1–2 entities / up to bounded enemies: deterministic masked order, max bound, no hidden hostile inventory read. Актёр, target, generation, party membership сменились — old observation/action rejected.
4. Candidate tests: empty/single eligible returns stock; critical heal stays critical; two legal heal targets distinguished; unsupported CONTROL/MOVE/PK not trained, old damage path works.
5. HEAL native receipt: exactly-one positive delta, actual MP, no self-invented HP, duplicate event key rejected, stale owner/epoch/skill/target cancels. Heals by unmanaged normal player unchanged.
6. Optimizer: loss decreases on synthetic **as math check only**, training seeds deterministic, weights nonzero/sane, no NaN, own checkpoint, load same inference, wrong schema/weight size fails. AUDIT independent, no retuning after audit.
7. Bench 128 logical actors × 4/8/16 legal actions; distribution p50/p95/p99, total features+model, CPU, allocations. Нет SQL или Geo pathfinding per action.

## NATIVE focused
8. Один собственной GameServer fixture, 1 trained heal class + >=2 eligible allies or healing spells, with actual legitimate healing and outcome, plus regression of previous 002 OFF/ON damage. No fake native HP writes or SQL rewards.
9. Два независимых анонимных контекста для train/audit. V3 candidate training offline + accepted TEST cold readback. В реальном GameServer модель меняет разрешённый HEAL target/skill; лог на русском связывает baseline→selected→exact recipient→actual restored HP, MP and event key. Emergency rules and existing player heal path untouched.
10. Одна дополнительная safety-focused проверка нескольких источников угроз/смерти/неполного cast: no double evidence, no extra attack, retreat/respawn identity intact.

## НЕ выполнять повторно без изменения relevant code
Full Windows Job-lifetime and WMI broker proof, multi-hour unattended parent exit, automatic SQL first install (125 scripts), restart-crash stress, 277/277 на каждый мелкий commit, native 128-visible scale. Эти результаты LEARNING-002 переиспользуются. Выполнить из них только дешёвые релевантные suites после финального diff и один clean native test в 003. Не тратить часы на audit/подгонку 10 одинаковых GameServer scenes.
