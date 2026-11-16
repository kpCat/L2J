# Контракт данных, оптимизации и безопасного исполнения

## Action sample

Каждая запись содержит `schema/fingerprint`, eventKey, episodeId (либо явный verified source root), actor identity/epoch, target identity/epoch, scope SOLO/PARTY (если достоверен), offered candidates, selected, stock, version/source SHA, frozen feature vector, local label из `TacticalReceiptV3.reward` и фактические HP/MP/time. Все numeric finite и bounded. Чужая owner generation, stale actor/target/skill/epoch, duplicate eventKey, forged row SHA — fail closed. Если EXP/SP неизвестны, поле optional UNKNOWN: оно не участвует в локальном label.

Отдельные counters: read, accepted, rejectedByReason, uniqueActors, uniqueEpisodes, train, heldout, updates, replaySkipped, scoreChanged, nativeSelected, nativePaid. Не маркировать as TRAINED если updates=0.

## Policy training

Содержательная цель для019 — bounded native DAMAGE/HEAL action reward, а не «победа всегда» и не магические контрфактические метки. По умолчанию модель score только в TEST/SHADOW и fallback при критическом emergency. Действия не из допустимого списка модель даже не видит. Self-heal в Solo допускается будущим HEAL executor только если он штатно подтверждён, но не расширять019 на отсутствующие capability paths.

В train/holdout разделять по **episodeId**; не делить отдельные действия одной сцены между train и audit. При слишком малом числе episodes разрешён минимальный экспериментальный 1-sample update с меткой `EXPERIMENTAL_NO_ADVANTAGE_PROOF`; релизное преимущество и promotion запрещены. Для позитивной неаварийной проверки V4 score требуется реальный сохранённый action sample, не сконструированный вручную. Для проверки противоположного выбора разрешена фиксированная lawful scenario fixture, отчётливо NOT_NATIVE.

## Компактный scorer

Реализация предпочтительно линейная (веса/маска/нормализация versioned, <=256 коэффициентов, no external ML dependency), Huber/clip, фиксированный seed и порядок обучения. Вектор содержит action-features и явные *interaction* с состоянием, иначе ранжирование разных actions в одном state не зависит от контекста. Не подбирать «магические» классовые веса под тест. После одного истинного update должны измениться bytes и SHA, счетчик updates и score на контрольном valid action. Привязка parentHash и training fingerprint к checkpoint обязательна.

Кандидат immutable, atomic temp-write→rename, replay `same eventKey` не меняет weights/updates. После crash: либо целиком подтверждённый checkpoint, либо отсутствие нового committed state; ambiguous/damaged checkpoint fail-closed и старый stock. Не хранить raw secrets, DB password или owner-nonce в публичном отчёте/коммите. Не менять 183 historical rows и V3 Adam.

## Rerank & safety

V4 hook в `TacticalPolicyV3` **опциональный**; OFF по умолчанию; immutable version loaded перед боем. V4 не создает кандидат сам и не отключает MP/Geo/skill filters. В TEST-mode логирует exact offered/score/stock/selected, reason, native receipt после исполнения. При пустом candidate set, unreadable model, timeout/budget, NaN, unsafe critical, insufficient confidence, missing observations → stock; в новом scorer **никаких** RETREAT/BSoE/Return в PvP.

Существующий 5ms боевой бюджет не увеличивать. Если ranker не укладывается, боевая логика fallback; время scorer измеряется отдельно. Не требовать, чтобы 1–2 удачных случайных каста доказали лучшую стратегию: считать measurable score change / lawful native influence, а superiority NOT_EVALUATED.
