# TASK065 — Phantom World: живое присутствие и безопасная ротация материализации

**Тип:** серверная продуктовая доработка M1, native + один обычный Main, не новая кампания M1–M7.
**Бюджет:** до 6 часов с первого действия; последние 60 минут — сборка, сохранение, остановка и публикация. Не выжидать бюджет, если Done достигнут раньше. Сжатие контекста/переподключение часы не обнуляет.
**Repo:** `kpCat/L2J`, `feature/phantom-world`.
**Base при составлении:** `5892949f89aa81e191705e67754aa1fd348186b6`; перед началом проверить live origin, ancestry и чистоту checkout.
**OWN:** `D:\Codex\repos\L2J-phantom-world` (Git), изменяемая часть — `L2J_Mobius_CT_2.6_HighFive`.
**Роадмап:** `docs/phantoms/PHANTOM_WORLD_ROADMAP_REVISED_2026-10-21.md` в модуле — предложение владельца, не заменяет официальные принятые матрицы/исторические FAIL.

## Продуктовая цель

В населённой области реального игрока система своевременно обслуживает законный спрос на видимых фантомов даже при занятом materialization cap — **через безопасное мягкое освобождение действительно невидимых свободных владельцев**, а не ожидание произвольного фонового idle. При выходе игрока персонажи продолжают обычную жизнь и постепенно уходят в фон по штатным правилам. Календарь даёт перекрывающиеся 12–16-часовые смены с покрытием всех 24 часов при достаточной популяции. Никаких новых дублирующих AI, scheduler, teleport engine или SQL-состояний.

**Не обещать** материализацию любого SLEEPING профиля, мгновенное заполнение произвольного города, выход за hard cap или вытеснение защищённой сессии. Ни synthetic player, ни заранее поданный XYZ не равны подлинной сетевой сессии человека.

## Сначала — причинная граница, не новая архитектура

Имеющиеся owners: `PhantomSystem`, `PhantomHumanLocalityControl`, `PhantomNativeLocalityEnvelope`, `PhantomPopulationEcologyService`, `PhantomReconcileFirstActivityPort`, `PhantomMaterializationRetentionPolicy`, `PhantomScheduler`, `PhantomPopulationCatalog`, `PhantomPresenceRegistry`, `PhantomMaterializationService`.

Точные наблюдения из baseline:
- `PhantomNativeLocalityEnvelope.couldKnow` использует соседство 1 native WorldRegion, `prewarm` — 2; расстояние и 3D-instance не подменять абстрактным городским радиусом.
- `PhantomScheduler` уже вызывает `requestSoftReclamation` при DEFERRED; `PhantomSystem` уже устанавливает candidate/retention и считает available permits + soft.
- Сейчас `Hold.reclaimable()` требует `RECENT_HUMAN` и отсутствия hard/local hold. **Проверить** случай cap full и soft вне поля зрения, который давно не видел человека; не считать это заранее доказанной единственной причиной.
- `PhantomReconcileFirstActivityPort` уже проверяет calendar/online, native historical reconciliation и current locality; его не обходить.
- Пакеты TASK061/064 доказали полезную самостоятельную жизнь, но ordinary064 source21 в момент измерения был SLEEPING/World0. Это не доказательство общего отказа присутствия.

Сначала снять факты переходов demand → readiness → preparation slots → scheduler cap → retention → materialize. Установить точную первую причинную причину выбранных FAIL. Изменять только соответствующий общий контракт.

## Требуемые изменения

1. **Грамотное вытеснение при давлении cap.** Новая demand возле наблюдаемого человека может запросить мягкое освобождение безопасного старого materialized owner, реально невидимого, без своей человеческой party/активного native action/следования/боевого claim/Store/reservation/accepted earned journey/ongoing persistence. Проверки перед выбором и перед исполнением на актуальном epoch/ownership. Всё сохранение и drain через существующий `dematerialize`, без принудительного удаления Player. Если нет safe victim — bounded WAIT_CAPACITY, без насильственной dematerialization.
2. **Приоритет и отсутствие ping-pong.** У новых локальных потребностей есть честная очередь/старение в прежнем scheduler/ecology, конечное время и cooldown/hysteresis при уходе человека. Не раздавать квоты одному профилю бесконечно, не делать отдельный thread/poller. Переиспользовать ограничения pulse/preparation/cap.
3. **Фактическая городская локальность.** Для Giran и одного контрастного места исследовать реальные topology anchors, WorldRegion, spawn/z/instance и visibility. Предварительная подготовка охватывает **законные соседние зоны большого города** без materialize всей области без спроса и без teleport/подмены позиции. Сохранить различие `prewarm`/`couldKnow`/actual client visibility. Исправлять только доказанную потерю спроса на соответствующей границе.
4. **Реалистичный 24-часовой календарь.** Расширить/сбалансировать существующие morning/evening/late cohort windows, чтобы новые профили в достаточной популяции распределялись по перекрывающимся 12–16-часовым ACTIVE-сменам, без пустого общего периода суток. Не все фантомы одновременно; фон/сон каждого сохраняется. Изменение v2 catalog/hash/persisted schedule допускается только с явным compat/versioned решением и проверкой старых идентичностей/состояния; ничего массово не пересоздавать. Переход по расписанию не отменяет accepted native/party work незаконно.
5. **Обычная полезная жизнь и restart.** На финальном целостном JAR один изолированный Main START→World-activity→штатный STOP→sameDB START и новые полезные действия при действительно допустимых input/time; если календарь законно исключает актёра — объявить границу отдельно, не переставлять часы/XYZ посреди сцены.

## Явно вне scope

Никаких новых рейдов, Rift/Labyrinth, экономики/OfflineMarket, новых фраз/Studio, клиентских изменений, повышения global cap ради скрытия отказа, ускоренных fake rewards, изменения M4–M7, массового мигрирования старых профилей, запретного G6 crash/Attach, многочасового soak. Никаких бесплатных телепортов для обхода cap или навигации.

## Доказательство и завершение

`ACCEPTANCE.md` + `TEST_CASES.md` — измеримые критерии, отличающие prewarm от actual World, FAIL от BLOCKED и настоящее сохранение от stale record. Тесты — исходный набор M1 ownership/scheduler/locality + focused changed paths + один bounded native pressure integration + обычный Main. В отдельном test fixture разрешена подготовка актёров **до** измерения; в самом эпизоде никаких форсированных materialize/XYZ/EXP/Player ownership.

Создать `docs/phantoms/reports/PHANTOM-LIVING-PRESENCE-065.md`, `docs/phantoms/tasks/PHANTOM-LIVING-PRESENCE-065/ACCEPTANCE065.tsv` с ссылками на реальные receipts. Подготовить отдельный остановленный candidate065 (при готовности), `START_HERE_065_RU.md`, JAR/source/DB/ports provenance и 1–2 коротких observer-сценария для будущей личной проверки. Ни одной старой PARTIAL не переименовывать в PASS только из-за focused tests.

Сделать exact-path commit(ы), обычный non-force push в `feature/phantom-world`, `HEAD=remote`, собственные JVM STOPPED, старые среды не изменены. При объективном ограничении — чистый PARTIAL/причина/HANDOFF, а не новая 12-часовая кампания.
