# Skill/target audit014

Scope нейросети остаётся SINGLE_TARGET_DAMAGE и ограниченное HEAL. Баффы, AOE, control, summon, resurrection и mobility не переименованы в поддерживаемые нейросетевые действия.

| Контракт | Доказательство | Статус/граница |
|---|---|---|
| Фактически изучено | `PhantomSkillInventory.acquired` читает `Player.getAllSkills`, проверяет classIndex и неизменность known refs. Baseline014 сохранил10 actual inventories пяти классов90/94/103/105/110,40–67 навыков на source. | PASS_ACQUIRED_FACTS; не all-classes native claim |
| Наследование старого навыка | В настоящем acquired inventory class103/classIndex0 присутствует1235 Hydro Blast, canonicalLevel28, enchantRoute0/enchantLevel0, SINGLE_TARGET_DAMAGE/target ONE. Теоретическое дерево отдельно от acquired. | PASS_READ_ONLY_ACTUAL; выдачи навыка не было |
| Enchant/subclass/stale version | Existing inventory contract проверяет canonical101/route1/rank1, изменённый fingerprint и отсутствие выдачи unlearned skill. Native eligibility сравнивает текущий learned level/ref, catalog reload остаётся UNASSESSED. | PASS_DB_FREE_CONTRACT; реальная заточка/смена subclass в014 NOT_RUN |
| Полный inventory и shortlist | Existing shortlist/curriculum:7 learned refs, два воспроизводимых окна по4 покрывают все7; cursor не сбрасывается, duplicate version и unlearned pool отказаны. | PASS_CONTRACT; новые fake skills не выданы |
| DAMAGE target | `TacticalPolicyV3.chooseDamage` требует настоящий Monster; `PhantomSkillInventory.eligibility` + native combat adapter проверяют owner, version, target/instance, MP/HP/items/charges/souls, reuse, weapon, native conditions, range и Geo/LOS. | PASS_CODE_CONTRACT; paid damage/effect014 до G3 NOT_RUN |
| HEAL target | `TacticalPolicyV3.legal` требует Player в той же Party, живого текущего owner, actual skill/level/HEAL effect, native conditions, reuse, range, LOS и MP. Enemy Monster не проходит этот путь. | PASS_CODE; actual paid friendly heal014 NOT_RUN |
| HEAL readiness | Baseline после134458.889ms естественного recovery отказал: SUPPORT_STOCK_HEAL_OFFERS. Full-HP, threat-free opening противоречит wounded-recipient условию Stock PartyTactics. | FAIL_ADMISSION; это не отсутствие learned skills и не ошибочный тип HEAL |
| Динамическая законность до CUT | `requireFreshEncounterFacts` повторно читает inventory, owner, target generation, Geo, current readiness и physical hash; cached mutable admission не добавлен. | PASS_CODE; новый полный headless/live parity ещё не заявлен |
| Исполнение и эффект | Native owner и actual paid effect/terminal receipt обязательны; synthetic Skill/MemberSnapshot fixtures доказательством игрового эффекта не являются. | UNKNOWN_NATIVE014 до законного G3 |
| Buff/action разделение | Штатный buff maintenance отдельно от V3 action families. | PASS_SCOPE; см. BUFF_AUDIT_RESULT |

Targeted Ant `phantom-autonomous-training-test` прошёл до causal fix: inventory, shortlist, eligibility, curriculum, objective, config. Они защищают contracts; не доказывают, что каждый живой каст во всех классах корректен. Итоговые G4 и native события фиксируются отдельно в RESULT/EVIDENCE после последней readiness.
