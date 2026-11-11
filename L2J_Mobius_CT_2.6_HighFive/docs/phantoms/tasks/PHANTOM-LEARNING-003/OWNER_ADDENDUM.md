# Обязательное дополнение владельца: native время и готовность

Дополнение принято в текущий PHANTOM-LEARNING-003, новый Goal не начинается.
Основная обучаемая вертикаль уже дала cold native HEAL2/4 changed и DAMAGE Solo/Party.
Для завершения требуется также следующее.

- Фактический remaining cooldown по native timestamp и disabled map с общим reuse hash;
  короткий откат не должен исчезать из-за отсутствия durable timestamp.
- Readiness NOW, прогноз следующей доступности, длительность каста и занятость — разные факты.
- PvE сначала отбирает готовые lawful навыки, затем применяет прежний round robin/V1 ranking.
  Когда готовых нет, существующий owner ждёт следующего pulse или использует разрешённый fallback.
- Base hit+cool и текущая native cast duration с Formulas/SPS/BSPS/clamps; без второго расчёта формул.
- Момент эффекта, busy/next-phase, прерывание/неудача, MP initial/final/HP и будущая готовность
  представлены отдельными полями с known masks и provenance. Для особых/channel/toggle skills
  нет выдуманной универсальной формулы. Неизвестный lethal horizon остаётся UNKNOWN.
- Bounded trajectory допускает последующее обучение последовательностей; сейчас сложные combos
  не обучаются. Предсказанный шаг не может объявить observed completion/failure.
- Focused RED/GREEN и real GameServer: один навык на настоящем КД, другой готов; нет пустой
  попытки неготового. Calm/pressure сцены выбирают соответственно штатный более медленный
  и доказуемо более быстрый Heal и исполняют настоящие HP/MP effects.

Read-first: Creature/MagicUseTask/TimeStamp, native disable maps/reuse hashes, V3 codecs,
PvE CombatService/ActorLease/L2jBackend, Party planner/backend/capability catalog и native skills XML.
Локальные аналоги: native PvP readiness predicate; существующая Party support capability selection;
реальный native cast lifecycle и ранее выполненные003 fixtures.

Bounded scope exception текущей одной вертикали расширяется на ActorLease readiness seam,
L2jPhantomPartyBackend/PhantomPartyBackend temporal query, TacticalTimingV3 и2 existing suites,
один native temporal fixture. Ни один новый supervisor/provider/движок/слой исполнения не создаётся.
Production skill time block извлечён как единый native query и повторно используется beginCast.
Нормализованный encoder state128/action64 и V1 codec не переопределяются: новый rich tail имеет
собственные magic/version/fingerprint, legacy restore→bytes остаётся побайтно идентичным.
Существующие checkpoints не переписываются и не перелицовываются под новую feature schema.

Непроверенное до финального run: native cooldown witness, calm/pressure Heal effects на новом JAR,
performance нового snapshot, итоговый scope/encoding/STOP/commit/push.
