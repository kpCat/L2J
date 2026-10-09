# CODEC_CONTRACT029

Контракт до production patch, на exact base2b9496c.
PNC1: исходные magic504e4331/header1, enum ordinals и поля не меняются;
исходная длина<=512, trailing bytes запрещены. Legacy constructors и encode
без policy сохраняют старые bytes. Legacy SUPPORTED требует points1.
PNC2: magic504e4332/header2, те же identity/phase/points/version/digests/epoch,
затем before/after nullable policy capsules. Общий размер<=1024.
Компонент SQL сохраняет фактическую schema1/2; SQL DDL не меняется.
Native owned receipt bytes/защиты не меняются.

Policy алгоритма1: level, canonical integer points, background float points,
ordinary reward eligibility, FARM-position validity, instance-zero TRAVEL
capability, configured vitalityEnabled, lucky/consumeStat, gain/lost rates,
vitality level1..4 rates, independent BONUS_EXP/SP percent и MAX_BONUS caps,
configured rates fingerprint64hex. Невит/advent, premium/party/summon и timed
reward modifiers без temporal model не получают ordinary FARM.
Position/anchor/identity/loadout provenance дополнительно связаны existing
background state digest и authority hashes; координаты не заменяются anchor.
Unknown capsule не даёт rewards. REST отсутствует.

Before/after snapshots независимы. PENDING не допускает background operation.
PNC1 pending receipts читаются и восстанавливаются тем же resolver.
Native capture начинает float с реально сохраняемого stock integer points;
между background batches сохраняется float, SQL использует штатный int cast.
FARM bonus вычисляется перед расходом; расход использует native target terms
и level после award. На level transition bounded batch заканчивается.
TRAVEL имеет нулевые reward/item/vitality/skill deltas и отдельную проверку path.

Допуск и commit проходят existing exact identity lease/profile-state locks/CAS.
Один DB commit публикует rewards/state/points/context/catchup. Proposed state
до commit не публикуется. Ambiguous commit разрешается exact operation key.

Проверки: B01..B06, T01..T06, COMPATIBILITY и actual crash lanes на finalSHA.
Старые872/1272 остаются UNPROVEN028. Route11 base replay PASS1/1 является NEW_PROOF;
старый first cause UNKNOWN, не объявлен исправленным.
Уточнение capsule algorithm2 до изменения codec: сохраняются native runSpeed,
maximumBatchMillis=60000 и bounded unsupportedFacts bitmask. Horizon относится к одному
batch: статические numeric facts действуют до смены level/class/loadout/rules binding;
любой timed active effect заранее unsupported. Level boundary инвалидирует FARM.
Algorithm1 раннего candidate029 остаётся readable, runSpeed0 не даёт новый TRAVEL;
новый configured fingerprint требует fresh native attestation для FARM. PNC1 без изменений.
Stored-origin return использует максимум4096 units, <=42 samples по100 units,
stock GeoEngine в обе стороны и ZoneManager WaterZone. До завершения учитывается
residualTravelMillis без XYZ/reward/resource deltas; commit пересчитывает тот же путь
и проверяет topology generation под тем же held background lease и DB boundary.
