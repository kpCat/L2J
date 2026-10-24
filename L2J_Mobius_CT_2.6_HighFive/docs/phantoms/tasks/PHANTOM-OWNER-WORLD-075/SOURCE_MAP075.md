# SOURCE_MAP075 — verified actual 8ac1132 source, read-first, no guessed API

Идентификаторы ниже проверены по GitHub `feature/phantom-world` на `8ac113257bea0cd3ea241b708889b42b42b5b961`. Они помогают чтению, но НЕ заменяют повторную проверку после `git fetch`.

| Путь относительно L2J_Mobius_CT_2.6_HighFive | Фактическое содержание / интеграция |
|---|---|
| `dist/game/config/Player.ini`; `java/org/l2jmobius/gameserver/config/PlayerConfig.java` | Tracked stock: AltWeightLimit1, лавки4/3 SELL и5/4 BUY, inventory80/100, WH120/100/200, recipe50/50. Точная загрузка в PlayerConfig; применить owner-цели из 38. |
| `dist/game/data/EnchantItemGroups.xml`; `dist/game/data/EnchantItemData.xml` | Stock 0–2/0–3 safe, 3–15/4–15 66%, 16+0; типовые scroll max16; различать special max и bonusRate100. |
| `dist/game/data/stats/skills/01500-01599.xml`; `dist/game/data/scripts/handlers/skill/effects/ServitorShare.java` | Native skill1557 + `Player.getServitorShareBonus`; сохранять stock coefficient и onStart/onExit. |
| `dist/game/config/Custom/PhantomMerchants.ini` | `MerchantLinesPerStore=3`; владелец хочет20, есть отдельные ограничения в production policy; не считать INI-only change PASS. |
| `dist/game/config/Custom/PrivateStoreRange.ini` | 50 от Player,100 от NPC уже стоят; сохранить native spacing. |
| `dist/game/config/Custom/PersonalCharacterQoL.ini` | WondrousCubic False/60 существует; сохранять allowlist и секреты. |
| `dist/game/config/Rates.ini` | Quest multipliers сейчас в основном1, `UseQuestRewardMultipliers=False`; это не доказательство исходных изменённых владельцем значений. Read-first Goal037 перед edits. |
| `java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java` | Действующая shared-scheduler логика временных лавок обычных active phantom: SELL/BUY; MANUFACTURE явно fail-closed. В 075 D развивать её жизненный цикл, а не подготовленных торговцев. |
| `java/org/l2jmobius/gameserver/phantoms/economy/PhantomOfflineMerchantService.java` | Другой owner — выделенные prepared offline merchants и lease; нельзя замещать им обычного накопившего товары фантома. |
| `java/org/l2jmobius/gameserver/data/sql/OfflineTraderTable.java` | Stock native хранение offline types/lines/restart; использовать только под ownership/DB guard. |
| `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java` | Существующие bounded cursor/claims/CAS/history; downtime E на них, без второй экономики. |
| `java/org/l2jmobius/gameserver/model/World.java`; `java/org/l2jmobius/gameserver/model/actor/Player.java` | В TASK074 добавлен published typed index SELL/MANUFACTURE, **BUY ещё не индексирован**; изменения 075 не ломают событие публикации, читают свежие native права/stock. |
| `java/org/l2jmobius/gameserver/phantoms/commerce/PhantomOfflineSupply.java` | TASK074 actual exact SELL/MFG/CRYST membership и multi-step component BUY; preserve cost/stock/hash/transaction revalidation. |

Финальная карта Symbol/API уточняется после source read; никаких вымышленных методов, отдельного второго engine или полной реконструкции истории квестовых rates.
