# PHANTOM WORLD / контроль реального прогресса TASK130–136

Baseline перед этим продолжением, проверен GitHub 2026-11-08:
- WIP `wip/coordination-130-preserved` HEAD `8140167705e6a6e76f370e3aa772b50f909b4d37`.
- feature HEAD `faec72161f9e4f6231764e17d6f2637a4db080af`.
- TASK130 **PARTIAL**: реальный GameServer authority recovery двух персонажей 70/72 (Player load/native STORE/readback), source/JAR parity 4736/4736, но `NATIVE130_NO_LAWFUL_FEASIBLE_SCENE`; profiles: 70=0 Party sources, 72=58; native Party/kills/Heal/restart текущей вертикали 0. Предыдущая Party была в другой, старой попытке и не заменяет final-JAR proof.
- TASK130–136 FULL: **0/7**.

| № | Продуктовый этап | Baseline | Что должно измениться при выполнении именно этого пакета |
|---|---|---|---|
| 130 | Intent/Presence/Background/Materialization/Party/Scheduler, durable group | PARTIAL | Group-only ±4, роли, 2–9, safe Party-combat, новая реальная Party+полезный PvE, restart, если возможно |
| 131 | Расширенная жизнь Party, support/PvP/PK, групповой spoil/sweep, PvE-сценарии | NOT_STARTED | Не запускать в этом пакете; подготовить совместимые native hooks, но не поглощать весь объём 131 |
| 132 | Кланы, альянсы, войны, штатные полномочия лидера | NOT_STARTED | Сохранить текущие native права |
| 133 | Рейды, Rift, групповые инстансы | NOT_STARTED | Не менять raid/instance target gating ради обычного FARM |
| 134 | Осады | NOT_STARTED | Не менять |
| 135 | Территориальные войны | NOT_STARTED | Не менять |
| 136 | Олимпиада | NOT_STARTED | Не менять, сохранять выход из Party по штатной логике |

В каждом отчёте и в начале КАЖДОЙ будущей задачи показать:
1. FULL count 130–136 до/после, PARTIAL отдельно.
2. Новый продуктовый факт: real party assembled? NPC damaged? earned reward? healer native effect? spoiler native receipt? graceful STORE? restart useful action?
3. Сколько новых коммитов, source/test файлов, MODEL/SQL, GameServer builds/boots и времени ушло.
4. Какая именно стадия заблокирована и как её изменить, если прогресс 0.
5. Сравнение фактического HEAD/diff с GitHub, не доверять тексту отчёта без сверки.

Запрет «вечной 130»: если два последовательных дешёвых цикла не дают нового измеримого шага, остановить patch-loop и провести cross-stage review; не генерировать сотни TASK130-R*. В рамках этого пакета допускаются коммиты и тестовые контрольные точки, но это ОДНА задача.
