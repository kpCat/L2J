# Передача после PHANTOM-LEARNING-003

Этот файл не начинает Goal004. Текущий итог и ограничения фиксируются в
`docs/phantoms/reports/PHANTOM-LEARNING-003.md`, runtime evidence и delivery receipt.

## Что сохранять

- V1/002 `NeuralPolicy`, feature/catalog fingerprint, immutable accepted-model-1.zip и DAMAGE ownership.
- V3 отдельно: TacticalObservationV3/LegalActionV3 → TacticalPolicyV3 → Party/Combat/native Skill,
  пассивный HealEvidenceV3 → offline TacticalStoreV3/TacticalTrainerV3.
- GameServer не зависит от trainer/SQL записи в decision path, LLM, UI или внешнего генератора.
- Новый action family сначала получает законного stock owner и exact native receipt; enum сам
  по себе не разрешает action и не доказывает trainability. Future outcomes остаются UNKNOWN.
- Native action owner и lease повторно проверяются перед исполнением и при HP/MP attribution.
- Историческая цель последнего Skill неизвестна; нельзя восстанавливать её из current target.

## Модель и данные

V3 contract version3,128 state/64 action,1 185 377 float32,4 741 508 weight bytes.
Schema/action/normalization/algorithm fingerprints плюс SHA обязательны при SQL/cold restore.
Нельзя выдавать старый V1 blob за V3 или дополнять padding до нужного размера.

TRAIN/TUNING/AUDIT раздельны по native actor и фактическому контексту. AUDIT одноразовый,
не используется для seed/hyperparameter search. Crit — настоящий stochastic Heal outcome,
не фильтровать и не заменять. DRAFT1 сохранён как не прошедший; его audit не возвращается в train.
`v3DatasetContextPrefix` выбирает заранее заданный corpus, не переписывает durable историю.
Round2 содержит сбалансированные97/105 и76–78 во всех splits, реальные HP/gear/distance facts.
Несколько актёров в одинаковом TRAIN context допустимы; между splits контексты disjoint.

Fit сохраняет DRAFT даже при успешном audit. Отдельный exact `apply-test <id> <sha>`
допускает checkpoint только в own TEST на stopped native boundary. ON pins задаются отдельно.
Автоматического применения модели, MAIN/PLAY promotion и overwrite Semantic Pack нет.

## Операторский маршрут

Java25/Ant/MariaDB11.4.13, existing Windows Training Director. Для новой собственной установки:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action Preflight -ConfigProfile V3
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\phantom-runtime\training\Training.ps1 -Action Start -ConfigProfile V3
```

Сначала заполнить optional read-only `assetSourceRoot`, проверить собственные свободные порты,
собственный root и остановленный native boundary. Существующие TRAIN_*.cmd по умолчанию V1;
ручная проверка этих CMD отложена владельцем, не считать её выполненной.
Не редактировать Training.ps1 при живом Director: его hash закреплён ownership contract.

Для следующего сбора нужны новый `v3CollectionRound`, новые actors и fresh audit; нельзя
удалять CUT fences или сбрасывать HP у actors с незавершённым previous CUT.
Partial batch не создаёт terminal phase marker; committed actor/context receipts сохраняются.
Completed manifest не переиспользовать для нового измеренного HP input.

## Что не доказано и не входит в003

Дополнения владельца о времени выполнены в003: TacticalTimingV3 optional tail, native-only
Creature.calculateSkillCastTime, shared/subsecond reuse, next-phase/interruptibility, ready filter
перед PvE RR/V1, urgent-fast/calm-stock Party support. Future combo trainer не реализован.
Native cooldown и fast/slow receipts см. EVIDENCE.json; источники/границы UNKNOWN — OWNER_ADDENDUM.
При следующем native proof нужен новый v3ProofRound: старые CUT и actors повторно не травмировать.

Обобщение на все профессии, PvP/Олимпиаду/осады, общий игровой интеллект, выигрыш над stock
в counterfactual продолжении боя, GPU training и семантическая сеть не заявляются.
128 logical actors benchmark не означает128 simultaneous decisions в каждом pulse.
Нужно сохранять cooldown, общий CPU slot и stock budget fallback;16 repeated action vectors
в benchmark не являются16 реально допустимыми HEAL в одной native Party.

Следующее расширение требует отдельного задания. Не менять Windows Job/G0/bootstrap,
если конкретный найденный дефект их не затрагивает. Не запускать protected releases/старые БД.
