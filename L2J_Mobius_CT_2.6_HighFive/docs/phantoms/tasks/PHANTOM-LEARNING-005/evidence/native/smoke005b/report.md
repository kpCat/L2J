# PHANTOM-LEARNING-005 — наблюдения EVAL

Статус реализации: PARTIAL до проверки всех G0–G8. Это не утверждение о превосходстве модели.

UNKNOWN не равен нулю. При n<20 вывод о значимом преимуществе запрещён. Bootstrap CI здесь описывает лишь малую наблюдаемую выборку.

Время цели — первое наблюдение canonical terminal receipt; точность ограничена штатным poll. Drain и общий settlement snapshot сохранены отдельно. HP/MP — сумма личных end-state после drain; net changes включают native regeneration. EXP/SP — сумма персональных кредитов, не награда NPC; observed heal охватывает только точные выбранные receipts.

## SOLO_DAMAGE_V1

Вердикт: **INCONCLUSIVE**, fair completed pairs n=0; arms=2; одиночных arms=0.

| Пара | Статус/причина | A исход/мс | B исход/мс | Δ B−A, мс | Изменено и исполнено |
|---|---|---|---|---|---|
|SOLO_DAMAGE_V1-0|UNMATCHED/PRE_CUT_FACTS_DIFFER|COMPLETED/17996.5832|COMPLETED/23599.4358|null|0|

Медиана Δtime=null; диапазон=null…null; 95% bootstrap CI=null…null. Очень малая выборка не позволяет обобщать.

- SOLO_DAMAGE_V1-0/LEARNED: COMPLETED/NATIVE_KILL; метрики {adenaNet=null, allActionsMpDebit=null, consumables=null, deaths=null, deathsLowerBound=0.0, drainMillis=1319.8409, elapsedMillis=23599.4358, exp=42624.0, expPerMinute=108368.6924413676, hp=4008.017071999999, loot=null, mp=2302.975840000001, netHpChange=-761.9829280000008, netMpChange=-485.02415999999903, observedHealMpDebit=0.0, observedUsefulHeal=0.0, settlementSnapshotMillis=24919.8068, sp=7595.0, spPerMinute=19309.783668641772, uniqueEnemyKills=1.0, wholePartyDamage=null}; receipt `7664c6f9ae24c33befdd5fb30d028987edf7ced3d7da302a5b404f9b66fe63f6.json`; native event keys [cd12987122402a50db6f83a0135a3d64058868b718aab65ab85b3d365b4a577c, fe6cff272450551861b23709522343018ca875eb9ad197de03a8cb84ad587baa].
- SOLO_DAMAGE_V1-0/STOCK: COMPLETED/NATIVE_KILL; метрики {adenaNet=null, allActionsMpDebit=null, consumables=null, deaths=null, deathsLowerBound=0.0, drainMillis=1108.3752, elapsedMillis=17996.5832, exp=45054.0, expPerMinute=150208.51291371798, hp=4467.268235999999, loot=null, mp=2472.2579840000008, netHpChange=-302.731764000001, netMpChange=-315.74201599999924, observedHealMpDebit=0.0, observedUsefulHeal=0.0, settlementSnapshotMillis=19104.9382, sp=7595.0, spPerMinute=25321.47324498797, uniqueEnemyKills=1.0, wholePartyDamage=null}; receipt `b8c05c828c6d31fa3197236b8a8ba59a6c5263ca49aaea3896e5933754f08de4.json`; native event keys [ea0c5f5cd2e8fc807ed4cdf08cf3d95c179d4d5e233e74a8664a7b03762791fc, 2d18d65cb6683eccbdba02a9014ce4a4d5224358ad119e3a3edce3ade644051f, 8345546c8aa2d4630303693bef5efc7cd74dd8e48c7fa3de189e30ba4b8d118f].
## PARTY_HEAL_V3

Вердикт: **INCONCLUSIVE**, fair completed pairs n=0; arms=2; одиночных arms=0.

| Пара | Статус/причина | A исход/мс | B исход/мс | Δ B−A, мс | Изменено и исполнено |
|---|---|---|---|---|---|
|PARTY_HEAL_V3-0|UNMATCHED/PRE_CUT_FACTS_DIFFER|COMPLETED/18687.8446|COMPLETED/19666.794|null|0|

Медиана Δtime=null; диапазон=null…null; 95% bootstrap CI=null…null. Очень малая выборка не позволяет обобщать.

- PARTY_HEAL_V3-0/LEARNED: COMPLETED/NATIVE_KILL; метрики {adenaNet=null, allActionsMpDebit=null, consumables=null, deaths=null, deathsLowerBound=0.0, drainMillis=1746.0088, elapsedMillis=19666.794, exp=110821.0, expPerMinute=338095.77707479923, hp=18140.755595295806, loot=null, mp=9436.307364, netHpChange=2882.5155952958066, netMpChange=-512.6926359999998, observedHealMpDebit=80.0, observedUsefulHeal=993.627281365201, settlementSnapshotMillis=21412.7579, sp=19746.0, spPerMinute=60241.643859187214, uniqueEnemyKills=1.0, wholePartyDamage=null}; receipt `5e35794da95aedbbc47fb60e870f6938e669a6a4db5bad5ba7fbe1e2a29c198d.json`; native event keys [224694c13d4ae17d5c684d080a904663443b5eac92c5ec8947a62c7110a36cb9, 016faccb2371b519f1067aaa142eef4dcd84f834a32fd9dd56aee83bba661e8d, 8c93db05b6722ee32ccc80f3032c0949b5669fc7c4b49e9c337f7416b8ab23cd].
- PARTY_HEAL_V3-0/STOCK: COMPLETED/NATIVE_KILL; метрики {adenaNet=null, allActionsMpDebit=null, consumables=null, deaths=null, deathsLowerBound=0.0, drainMillis=1428.3486, elapsedMillis=18687.8446, exp=110821.0, expPerMinute=355806.6830243227, hp=20387.44364353909, loot=null, mp=9451.972776, netHpChange=5129.203643539089, netMpChange=-497.0272239999995, observedHealMpDebit=80.0, observedUsefulHeal=2980.881844095603, settlementSnapshotMillis=20116.1659, sp=19746.0, spPerMinute=63397.35937230557, uniqueEnemyKills=1.0, wholePartyDamage=null}; receipt `9f2f851303218e34ced91f33cfc15b2c3af82f7dcfa18aa2bf7a911e2b3f51ea.json`; native event keys [bd7a865f57b25e5bb342d60392757e285ab74283861fda3d20d3f2e94e0f2a6e, dde020382542f26f482297f5b823e6d8e6ebb288b9f96caf95bab5405b3ec7d3, 9885c47bcc22793da4d2a96bff28a31a26e2f0a9b0de9871eafc18332bbc88ab].
