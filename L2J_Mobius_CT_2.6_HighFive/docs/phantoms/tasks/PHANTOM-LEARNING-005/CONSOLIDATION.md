# Консолидация в feature/phantom-world

Отдельное поручение пользователя: объединить накопленные изменения после feature/phantom-world в эту ветку и разобрать старую конфликтную рабочую копию автономно, сохранив её содержимое.

Проверенная опубликованная цепь TASK130 → LEARNING001–004 уже находится в ancestry005. Между исходным feature faec72161f9e4f6231764e17d6f2637a4db080af и004 dff800f4b95232c132b374f8ec97c932eda4a6b1 —162commits ahead,0behind.005 source commit 4a910adaea9dfb0ec5341439ffebd83d11c7cac8 опубликован обычным push и независимо прочитан через GitHub GET. Три зарегистрированных старых worktreeHEAD a1b30f7b84a9c06e1141a0cae7a2e48dd922bf24, a7cb9a7c2bd4ced33ed693c4d6f3d22f809981a5, d493fffa101c388e9d005ad1a904ffa23f28038f уже являются ancestors исходного feature.

## Сохранение старой работы

Основной checkout содержит старый index23tracked paths/11UU, безMERGE_HEAD. Пользователь подтвердил, что это старая работа. До любых замен сохранены byte-exact working23, все stages и самindex;10untracked документов TASK130 также сохранены вне checkout сSHA. Восстанавливаемый [архив](artifacts/preserved-local-index.zip) содержит working/stages, документы, receipt и единственную старую незакоммиченную зависимость PhantomTask109Lane. Полныйindex16MB остаётся дополнительной внешней копией. [Receipt](artifacts/preserved-local-index.json) фиксирует82entries и их полную byte verification.

Архив SHA-256: 0a81523bbea45e9053adc5318f770e4330cf3ea4892ee34c6e910aa78d7a2e7f; bytes=971624. Не включены private configs/credentials, SQL/datadir, Geo, JAR или логи. Старые82entries не считаются новым активным кодом; их исходные bytes не перекодировались.

## Решение по23 файлам

В основной код берётся проверенная incoming версия. Сохранённые7неопубликованных предложений требуют отдельных задач и focused tests: DEATH_DROP Party acquisition/support; proactive dispute; terminal-order PvP; TASK109 lane hooks с отсутствующей в опубликованном source dependency. Их внедрение смешало бы новые игровые изменения с housekeeping и частично вернуло устаревшие API/удалённые safeguards. Никакие исходники не потеряны: полный draft находится в архиве.

Для16остальных версия доказанно историческая либо регрессивная: старые TASK098/104/066r2/112/075; удаление karma/historical idle/reconsideration safeguards, TASK123/126/128 контрактов; повторный revenge loop с устаревшим SourceEvidence и потерей exact SubjectRef/authorityCurrent. Независимый read-only review подтвердил классификацию; семантически разные старые предложения не выдаются за реализованные.

| Exact path | Классификация | Рабочий SHA-256 до замены |
|---|---|---|
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/L2jPhantomBackgroundAuthority.java | REGRESSION_OF_PUBLISHED_SAFEGUARDS | a2be85c83e6ee4dd77b500bffd2254d330891e947cf640a2df1a78b259af8f91 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundService.java | REGRESSION_OF_PUBLISHED_SAFEGUARDS | 4a6ea9b73b03e1ecdc3249fedf4674e2a4e1cfa59fc110b818a048b90f4f9419 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java | REGRESSION_OF_PUBLISHED_SAFEGUARDS | babb07f5e81c148eff8aa04e8ca54cb19b0b7b0d99c313c9fb323e5e213e8013 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundService.java | REGRESSION_OF_PUBLISHED_SAFEGUARDS | 75e0107a58071754ddd5587f6a72afb7f96b506af23606d497388f8c8977ff25 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java | HISTORICAL_VERSION_SUPERSEDED | 891d288959c94cf144b912e3136310188a5e960120f324ac75529a4c23cc5deb |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java | HISTORICAL_VERSION_SUPERSEDED | eaa2ba8dde60f378682a8bf5120979cb129edd5608fc93967b14d906e12c94ed |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/farming/PhantomFarmingService.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | c45e366db47ed8a4c24a1108890faa9f39299cfd64931058814c9de5b3691056 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/navigation/L2jNavigationBackend.java | HISTORICAL_VERSION_SUPERSEDED | d6e8c6f78cfe6e0b5d24842915dae63ddd01aeb53168bca23b183d19056220df |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | 4e57360dfcc3b0c8c24d06ede7e4936eef5daee0726b4fe7ddc4388183bd30c1 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | 93ad84ea6589ecddbb4fb9b06243b37a2facbf6f8f1111e29c7fdd8934030d85 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java | HISTORICAL_VERSION_SUPERSEDED | fd4dd6be8d8f62b463924ba7b3462ad61f2bd509008a290adcdecc4068fad818 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationStore.java | HISTORICAL_VERSION_SUPERSEDED | 7ce451d1130f3b645262bee874bc11d6569369a4b4fa1d7a9bd4f912c986e27d |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpContext.java | STALE_DUPLICATE_REVENGE_INTEGRATION | 5630cf379fcce5398b4f347d578b0120fb92f3847cb46af05532fe4b2440c0b7 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpService.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | 4ec37d9b5023689b5de7836fb0ce40a0da9f611a6a5c8893ae53370b6a439ca1 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/social/PhantomPvpSocialBridge.java | STALE_DUPLICATE_REVENGE_INTEGRATION | b499c1223c1dea58b03f67398faf73095fe06424fe470ea0dcd76c8a366545d4 |
| L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/social/PhantomSocialService.java | STALE_DUPLICATE_REVENGE_INTEGRATION | e87d3f5a936c1974b2e1e387ae58e28e5fc4a7dbf8a2dad23322903b0024b9a1 |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/background/PhantomLivingWorld112Checks.java | HISTORICAL_VERSION_SUPERSEDED | 6587cceab638b14693b9ade85656dcf9595cc51df5c3e6f16b21836338dfecd2 |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/player/PhantomPickup109Checks.java | REGRESSION_OF_PUBLISHED_SAFEGUARDS | efa66d0e93cd401cc079b4eb685e1a17ba2bf336502e5a6b7ac74c33a9e34fec |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/gameserver/phantoms/pvp/PhantomPvpCorrectiveSuite.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | d51caa0f30c2ede9f8e222e550a52ab0f5d3ffb66058161cf3ebbe6734b054c2 |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomEarnedRecipient109Trace.java | REGRESSION_OF_PUBLISHED_SAFEGUARDS | 9d6d5f2d40281222f8135d2900a7b07e2384fa0d0d300db61de6560fdfcc2587 |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | bb622a966f5d35b8c16f464a513a0694b17580052fa1186e49eadca6a2f48bf6 |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPartySuite.java | UNFINISHED_PROPOSAL_PRESERVED_NOT_ACTIVATED | 110b1973a2710fb76de9d4f922290b1aa72b31fd862bfc851d9108ab7f714ddf |
| L2J_Mobius_CT_2.6_HighFive/test/java/org/l2jmobius/tests/phantoms/PhantomPopulationEcologyGoal033Suite.java | HISTORICAL_VERSION_SUPERSEDED | 4969fc32365ad92751df5b1043a3b57bcf27429277b79f021110fe9b14fc1230 |

## Bounded применение

Разрешены только23точных source resolutions по таблице и10точных untracked collision moves после полной SHA verification. Применение canonical incoming → exact git add23 → git merge --ff-only проверяет, что сохранённые решения совпадают с входящим деревом; нет reset/clean/stash/force/history rewrite. Остальной untracked dirt остаётся на месте. После FF task/source/evidence bytes синхронизируются с проверенным clone для сохранения input hashes при core.autocrlf; Git tree остаётся тем же.

Final verification: exact HEAD/branch, отсутствие unmerged/staged tracked dirt,10source SHA,17input hashes,207protected guard, focused28 и существующие affected checks на идентичном source tree. Последний GameServer JAR из испытаний не пересобирается поверх защищённой поставки оригинального checkout.

master/origin/master/upstream/master/default branch не меняются. Публикация feature — только обычный non-force push; независимый GitHub readback подтверждается после него. Старые SQL/evidence clones и чужие worktrees сохраняются. Их физическое удаление не входит в это объединение.

## После отдельно разрешённого объединения

Локальный feature/phantom-world fast-forward до005 source commit4a910adaea9dfb0ec5341439ffebd83d11c7cac8 выполнен;23source resolutions и10collision документов сохранены,11UU устранены. Tracked dirt0. Merged focused contracts28/28 и17input hashes подтверждены; source tree соответствует уже собранной и проверенной005 версии. Восстанавливаемый старый draft опубликован отдельным архивом,7незавершённых предложений не активированы.

Важно для будущего TEST: опубликованная цепь добавляет17строк Ant в tracked build.xml исходного checkout. Поэтому после этой отдельной consolidation старый frozen207manifest видит206unchanged и1ожидаемое source изменение build.xml (13b0144d… →04eb66eb…). Сами старые manifests не переписаны; защищённые release runtime/SQL/Geo не менялись. Все native005 и coldResume прошли ДО consolidation с207/207PASS. Новый запуск старогоTEST требует отдельно аттестованного baseline; guard не обходить. Это отражено отдельно от исторических G0 результатов в DELIVERY/EVIDENCE.
