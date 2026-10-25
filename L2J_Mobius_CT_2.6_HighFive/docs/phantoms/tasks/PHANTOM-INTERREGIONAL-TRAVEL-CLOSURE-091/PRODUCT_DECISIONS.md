# PRODUCT_DECISIONS — invariant owner contract

1. **Реальная самостоятельная жизнь:** цель — 5000 durable Phantom identities, initial cap128+ настоящих Player и 200+ native offline stores, но TASK091 закрывает только фундаментальное передвижение. Не имитировать население или earned activity.
2. **Существующий Java/native engine:** GeoEngine/PathFinding/TeleporterData/World/Player/Quest reward. Не создавать второй travel/persistence/AI engine, глобальные shortcuts, готовые маршруты для трёх координат или внешнюю LLM.
3. **First-mile и long-distance:** эльф может оказаться на другом Z-слое. Исправление обязано быть general 3D/native, с подтверждённым WALK и фактическим движением, без fake Geo bridge. `SEARCH_LIMIT` не значит непроходимость.
4. **Paid travel:** неподтверждённый native GK не добавляется в certified background graph. Оплата только реальными Adena через native NPC, актуальный castle/karma/range/availability. Стоимость расстояния учитывается при выборе, **никакой произвольный hard cutoff не лишает доступного региона**.
5. **Не нарушать earned economy:** нет бесплатных денег/вещей/teleport, двойной оплаты, второй inventory authority, выдуманных фарм-наград. TEST стартовые средства помечены отдельно.
6. **Safe restart:** профиль, цель и Player identity сохраняются; старые Player/background состояния читаются; неоднозначный paid intent fail closed и не платит снова. Party/Store/Combat/owner scope не прерывать.
7. **Владелец и config:** всю TASK088/090 approved config, Rates.ini `RateQuestReward=50`, `UseQuestRewardMultipliers=False` и все 272 overrides сохранить неизменными. Никаких QoL/CommunityBoard/player configs вне этой задачи.
8. **Против partial ради тестов:** единый end-to-end эпизод ценнее сотен новых synthetic tests. Сохранять честный `PARTIAL` если фактическая цепочка недоказана, без возведения workaround к PASS.
9. **Защищённые среды:** не трогать `D:\PhantomWorld\Playable-078-owner`, её DB, чужие рантаймы, исходную D:-репу за пределами exact task. Старые C:-пути и worktrees — history only.
10. **Финальная поставка:** не создавать новую пользовательскую сборку до заключительной интеграции; промежуточная чистая shadow/JAR для native доказательства разрешена.
