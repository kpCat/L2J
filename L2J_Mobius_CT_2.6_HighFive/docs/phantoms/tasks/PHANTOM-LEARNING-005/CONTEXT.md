# Контекст и непротиворечивые факты на 12.11.2026

## Актуальные SHA/среды
- Base GitHub: `kpCat/L2J`, ветка `wip/phantom-learning-004`, HEAD `dff800f4b95232c132b374f8ec97c932eda4a6b1`.
- Protected `feature/phantom-world` `faec72161f9e4f6231764e17d6f2637a4db080af`, TASK130 `wip/coordination-130-preserved` `c5b81f89ce02030eff8441395b0411137a110b2f`, 003 `06d42410fbfe1ca9b7f462b0816e0da1784630d6`, 002 `b7b5575457c12c1b0438bcbaa646675a67778391`. Перед операциями reread.
- Пользовательский checkout: `D:\Codex\repos\L2J-phantom-world`, только read-only. Original 004 independent clone `D:\Codex\repos\L2J-learning-004` также НЕ изменять; если нужен SQL/Geo источник, только authorized read-only + exact copied owned snapshot в НОВЫЙ clone, без подмены внешних secrets и без работы с чужими процессами.
- 004 PORTS owned TEST: SQL33337/game17981/login19281; новый независимый TEST выбирает свои свободные loopback порты с проверкой владельца, не предполагает, что прежние обязательно свободны. JDK25, Ant, MariaDB11.4.13, Geo203/protected207.

## Что ещё не доказано
- FULL004_CORE — работоспособный trainer, не доказательство улучшенной тактики. `TacticalEpisodeV3` не применяет terminal outcome к reward.
- Новая V3 candidate8 DRAFT: selected SHA `ab62799a566c4cf52807978a12bea023be864d58915b3a72beaf7934d6a96016`, optimization SHA `56cc26c43a2b2edfba23bb2a6e56a207dd10ca7243af7796611a10d7b1f58c0f`; это не eligible ON.
- Last accepted V3 №2 SHA `6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc`, model ZIP SHA `04393f2cbc7ba8c4760e42c6f32ae93edd4b8bf848e50d25e97ee8b4348716b5`, trained mostly HEAL, per-class audit Cardinal97 неровный.
- V1 DAMAGE accepted №1 SHA `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`, V1 ZIP SHA `10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da`.
- Current V3 model/optimizer physically ~4.52MiB weights + ~14.2 MB Adam offline, scorer native p95 1.818ms in 004 bounded sample, no 128 native simultaneous proof.
- 004 real native history 509 exact effects and 26 episodes (23 completed / 3 incomplete). Full segments: 14 complete/3 timeout/1 pre-CUT refusal. No stock win-rate, Party whole damage, full deaths, loot/adena net, consumables proof. `stockCounterfactualMse=null`. These prior data are NOT paired evaluation results.
- New test must establish actual baseline and conditions for matched comparisons, not reuse 004's unpaired episodes as if matched.

## Требования владельца (для будущих задач, не этот scope)
Один shared combat intelligence для 5000 profiles, ≥128 visible plus 250 offline merchants not counted in cap. Обучение ночью много дней подряд; без ручных 10 млн комбинаций, без завышенных обещаний. Логика класса/уровня/gear/jewels/buffs, КД и время каста. В будущем учить фарм / Warlord AoE / рейды / PvP 1×1, 1×2, N×M, FFA / кланы / войны / осады / TW / Олимпиаду / rescue and Return. Semantic Pack (русская разговорная речь, мат, persona, L2+real) после Олимпиады. Эта задача — только честный PvE benchmark, основу будущей league evaluation.
