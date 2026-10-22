# TASK068 — состояние поставки

Кампания ещё выполняется; окончательные STOP/push и A–G matrix будут дописаны перед завершением. Основной результат: [RESULT068](../../reports/PHANTOM-LIVING-ECONOMY-068.md), история причин и запусков: [PROGRESS068](PROGRESS068.md).

Последний code commit: `2bb91c094cbf906709e59d743d040bdee1845193`. GameServer SHA256 `971125E76DF4DA7E0EA02E43512530A7AFBB91C3024EACA554F99AD22E0B69F0`. Источник сборки — exact committed archive `core-v4`, сохранённый в собственном `.phantom-local/task068`; final report-only descendant не требует пересборки JAR.

Самостоятельная поставка: `D:\PhantomWorld\Living-Economy-068-candidate`. После финального STOP оператор использует её START.cmd / STATUS.cmd / STOP.cmd и START_HERE_068_RU.md. TEST `.phantom-local/task068/runtime-test` и component `native-test` — служебные068среды, не пользовательский baseline. Старые057–067/MAIN/PLAY/client/shared MariaDB не изменялись.

Профиль candidate: Phantom8/8/8, FRESH/age0/UTC, finite native merchant target200/hard250. Starter0 — измерительная настройка; R2 lifetime5000 сохранён, значение5000 задают до рождения новых боевых identities. Blue и personal QoL/Rift switches OFF/allowlists пустые. Аккаунт владельца не угадывался.

Alt+B сохраняет stock XML и CustomCommunityBoard=False. В candidate уже подготовлены `pricing/canonical` и пример `pricing/stock-plan` **без Apply**. При изменении Rates.ini надо повторно использовать этот baseline, выбрать новый output Plan, просмотреть diff/UNPRICED/warnings, явно выполнить Apply exact SHA, затем START. Не пересоздавать baseline после каждого Apply. Rollback возвращает цены по exact receipt/postimages; Rates.ini возвращается отдельно. Инструкция — contained `tools/phantom-economy/README_RU.md`; подробные команды и x10→x20→x10 evidence — [E_HANDOFF068](E_HANDOFF068.md).

Finite release manufacture-profession preparation выполнена до отказа native skill transaction и оставила PENDING. Сохранён тот же `Craft068Maker`/268480990 на disabled account: class53/level5/SP310, skill172 и recipe1 отсутствуют, инвентарь пуст. Причина helper: fresh Player.create оставляет activeClass0, durable classid53; transaction отклонила обучение до списания SP. Готовится адресное продолжение через canonical Player.load с exact preconditions и сохранением исходной provenance; credits/новое имя/возврат ресурсов не выдаются. Затем только actual characterId READY можно добавить в candidate MerchantPreparedOfflineCharacterIds. Точный contract — [MFG_PREP_HANDOFF068](MFG_PREP_HANDOFF068.md).

Остаток текущей кампании: native local-return/Main continuation на v4, финальный sameDB/sameJAR restart/useful action, candidate200/MANUFACTURE restore, сохранность4+6+203 и собственные JVM/listeners/DB0, final matrices/roadmaps, addressed commit и non-force push только feature/phantom-world. Новые Goal/автоматизации не начинать.
