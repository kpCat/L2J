# L2-POST-002 — Living Support и Semantic Pack v3

Статус: реализация и все обязательные gate’ы завершены; пакет готов к единственному commit и normal push.

## Аудит владельцев

### ALREADY_PRESENT

- `PhantomPartyCoordinator` и `L2jPhantomPartyBackend` уже используют канонический live `Party`; обычные Player представлены как `MemberRef.real`, поэтому отдельная модель «human party member» не нужна.
- Progression/Game Knowledge уже выводят `combat.heal`, `combat.recharge`, `combat.resurrection`, `combat.buff`, `combat.song`, `combat.dance` из загруженных High Five skill trees и точных `Skill` facts.
- `L2jCombatBackend` уже владеет native cast через `PlayerAI` и `Intention.CAST`; native `checkDoCastConditions`/`Skill.checkCondition` сохраняют MP, items, reuse, range, zone, Olympiad, instance и interrupt semantics.
- Conversation уже имеет durable proposal/execution receipts, terminal replay guard и точное заземление counterpart.
- Social уже предоставляет persisted relationship и deterministic personality traits. Отдельного канонического mood owner нет.
- Humanized v1/v2/custom уже имеют строгий XML parser, content hashes и explicit custom override.
- Population уже хранит точные archetype weights `55/8/8/12/7/10` и проверяет сумму `100`.

### CHANGE_REQUIRED

- Party tactics обслуживали heal/recharge/resurrection, но не поддерживали buff/song/dance maintenance и не читали оставшееся native abnormal time.
- Raid reservation не включал buff/song/dance.
- `party.support` в execution v1 оставался `DEFERRED`; фраза «бафни меня» не создавала bounded functional action.
- Humanized v2 использовал bounded, но линейные списки и лимит 512, недостаточные для 20k/5k.
- Не было manifest-owned v3 order, v3 atomic validation, combined v1+v2+v3 hash и быстрого content-only validator target.
- Русская operator-документация не объясняла явно, что `ENHANCEMENT=12` не означает 12% Prophet.

### OUT_OF_SCOPE

- Реальный runtime LLM/API, генерация полного 20k/5k корпуса, production DB и provisioning.
- Client patch, economy rewrite, новые skill/class tables, прямое создание effects, бесплатные ресурсы или cooldown reset.
- Выдуманный mood state и переработка raid mechanics.

## Реализованная архитектура

- Data-owned `high-five-party-support-v1.xml` задаёт rebuff threshold, лимиты maintenance и relationship/personality thresholds.
- Party/raid выбирают только реально learned/ready canonical capabilities; critical heal/resurrection/recharge остаются выше обычного maintenance.
- `PhantomSupportEffectAuthority` читает native `BuffInfo`, abnormal type/level и remaining time. Healthy equal/stronger effect возвращает idempotent результат; missing/near-expiry допускает native cast.
- Conversation execution v2 исполняет exact `party.support`: same-party не блокируется отношением, external target проходит persisted relationship/personality policy; UNKNOWN/TENSE/HOSTILE fail closed, RIVAL задан policy.
- Semantic v3 загружается атомарно в manifest order поверх frozen v1/v2, затем custom overlay. Startup строит immutable exact/prefix/suffix understanding indexes и act/band/register/mature response buckets; XML/filesystem в chat hot path отсутствуют.
- Текущий v3 — только небольшой seed для support и проверки архитектуры; массовый корпус отложен в отдельную Spark content-only задачу.

## Проверки

Targeted gate’ы запускались до полного verify:

- `ant phantom-post002-support-test`: PASS, 5/5;
- `ant phantom-post002-native-support-test`: PASS, 1/1; production conversation execution port выдал нативный cast реальному human party member, после чего live `BuffInfo` подтвердил effect; также проверены healthy suppression, near-expiry, MP, range и reuse;
- `ant phantom-post002-conversation-test`: PASS, 4/4;
- `ant phantom-humanized-v3-content-validate`: PASS, 3/3;
- `ant phantom-post002-semantic-v3-scale-test`: PASS, 1/1; синтетический каталог загрузил `5 110` patterns и `20 163` templates, сохранив bounded indexed lookup;
- `ant phantom-post002-population-test`: PASS, 1/1;
- `ant phantom-post002-freeze-test`: PASS; QOL-007 semantic v1/v2 5/5 и Goal038 catalog 5/5, frozen hashes не изменены.

`ant phantom-post002-affected-test`: BUILD SUCCESSFUL (`2:21`), party/raid/conversation/social/progression/materialization green.

`ant phantom-post002-release-freeze-test`: BUILD SUCCESSFUL (`16:20`), релевантная QOL-009/Goal039 freeze-цепочка выполнена ровно один раз.

Текущий shipped v3 seed содержит 14 patterns и 15 templates, всего 29 новых content entries. Полный объединённый каталог содержит 124 patterns и 178 templates. Детерминированный combined hash без custom overlay: `e6b039fc790967f0ad3bcda1a2ba616fe3c5de98d1016dec823760124d492e79`.

Полный `ant verify`: BUILD SUCCESSFUL (`36:27`), выполнен одним свежим запуском после targeted и release-freeze gate’ов.

Production DB, `prepare-phantom-test-db`, client patch, runtime LLM/API и массовая генерация корпуса не использовались. Native integration работал только с уже существующей изолированной test DB.
