# PHANTOM-LEARNING-005 — наблюдения EVAL

Статус реализации: PARTIAL до проверки всех G0–G8. Это не утверждение о превосходстве модели.

UNKNOWN не равен нулю. При n<20 вывод о значимом преимуществе запрещён. Bootstrap CI здесь описывает лишь малую наблюдаемую выборку.

Время цели — первое наблюдение canonical terminal receipt; точность ограничена штатным poll. Drain и общий settlement snapshot сохранены отдельно. HP/MP — сумма личных end-state после drain; net changes включают native regeneration. EXP/SP — сумма персональных кредитов, не награда NPC; observed heal охватывает только точные выбранные receipts.

## SOLO_DAMAGE_V1

Вердикт: **INCONCLUSIVE**, fair completed pairs n=0; arms=2; одиночных arms=0.

| Пара | Статус/причина | A исход/мс | B исход/мс | Δ B−A, мс | Изменено и исполнено |
|---|---|---|---|---|---|
|SOLO_DAMAGE_V1-0|UNMATCHED/PRE_CUT_FACTS_DIFFER|INCOMPLETE/120058.4058|SKIP/0.0|null|0|

Медиана Δtime=null; диапазон=null…null; 95% bootstrap CI=null…null. Очень малая выборка не позволяет обобщать.

- SOLO_DAMAGE_V1-0/LEARNED: SKIP/LEARNING001_EVAL005_FRESH_STOCK_TARGET; метрики {}; receipt `7664c6f9ae24c33befdd5fb30d028987edf7ced3d7da302a5b404f9b66fe63f6.json`; native event keys [].
- SOLO_DAMAGE_V1-0/STOCK: INCOMPLETE/TIMEOUT; метрики {adenaNet=null, allActionsMpDebit=null, consumables=null, deaths=null, deathsLowerBound=0.0, drainMillis=4132.0919, elapsedMillis=120058.4058, exp=0.0, expPerMinute=0.0, hp=2047.1582803241263, loot=null, mp=2788.0, netHpChange=-2722.8417196758737, netMpChange=0.0, observedHealMpDebit=0.0, observedUsefulHeal=0.0, settlementSnapshotMillis=124190.7188, sp=0.0, spPerMinute=0.0, uniqueEnemyKills=0.0, wholePartyDamage=null}; receipt `b8c05c828c6d31fa3197236b8a8ba59a6c5263ca49aaea3896e5933754f08de4.json`; native event keys [dd9a627b0d5c48c83f9e7bda4fbf077cda3f03553142adf48ec6ef0d4cc0874c].
## PARTY_HEAL_V3

Вердикт: **INCONCLUSIVE**, fair completed pairs n=0; arms=2; одиночных arms=0.

| Пара | Статус/причина | A исход/мс | B исход/мс | Δ B−A, мс | Изменено и исполнено |
|---|---|---|---|---|---|
|PARTY_HEAL_V3-0|UNMATCHED/PRE_CUT_FACTS_DIFFER|SKIP/0.0|COMPLETED/32035.0633|null|0|

Медиана Δtime=null; диапазон=null…null; 95% bootstrap CI=null…null. Очень малая выборка не позволяет обобщать.

- PARTY_HEAL_V3-0/LEARNED: COMPLETED/NATIVE_KILL; метрики {adenaNet=null, allActionsMpDebit=null, consumables=null, deaths=null, deathsLowerBound=0.0, drainMillis=2967.0229, elapsedMillis=32035.0633, exp=110821.0, expPerMinute=207561.9435407827, hp=18026.430190223706, loot=null, mp=9340.121140000001, netHpChange=2768.190190223706, netMpChange=-608.8788599999989, observedHealMpDebit=0.0, observedUsefulHeal=0.0, settlementSnapshotMillis=35001.8002, sp=19746.0, spPerMinute=36983.22643863794, uniqueEnemyKills=1.0, wholePartyDamage=null}; receipt `5e35794da95aedbbc47fb60e870f6938e669a6a4db5bad5ba7fbe1e2a29c198d.json`; native event keys [535f5ef0e53d514b228ea2a87ad91dd60640618a5616f7c6a647a5101335c9fc].
- PARTY_HEAL_V3-0/STOCK: SKIP/LEARNING001_EVAL005_FRESH_STOCK_TARGET; метрики {}; receipt `9f2f851303218e34ced91f33cfc15b2c3af82f7dcfa18aa2bf7a911e2b3f51ea.json`; native event keys [].
