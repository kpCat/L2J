# Быстрый маршрут по исходникам

**Repo:** https://github.com/kpCat/L2J
**Git-root пользователя READ-ONLY:** `D:\Codex\repos\L2J-phantom-world`
**Рабочий модуль:** `<clone>\L2J_Mobius_CT_2.6_HighFive`
**Source head:** `b85dd2a3a5f6f153c77b02f1e4887c490eb08ea8`

Краткий read-first:

- `docs/phantoms/tasks/PHANTOM-LEARNING-001/HANDOFF.md` — точный финальный proof, версии/sha, cycle19, native methods and ownership.
- `docs/phantoms/reports/PHANTOM-LEARNING-001.md` — история проблем/ошибок fixture; не повторять их без новой гипотезы.
- `java/org/l2jmobius/gameserver/phantoms/learning/LearningPolicy.java` — режимы OFF/SHADOW/ON и текущая оценка 2–4 навыков.
- `java/org/l2jmobius/gameserver/phantoms/learning/NeuralPolicy.java` — 128→24→1, v1/v2 residual, offline training.
- `java/org/l2jmobius/gameserver/phantoms/learning/LearningFeatures.java` — существующие 64 pairs, schema mask.
- `java/org/l2jmobius/gameserver/phantoms/learning/LearningCastEvidence.java` — атрибуция точного native эффекта.
- `java/org/l2jmobius/gameserver/phantoms/learning/LearningStore.java` — хранение данных, checkpoint, valid model acceptance.
- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java` — текущая production dispatcher и training seam.
- `java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java` — native feature facts + legal cast.
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java` — native claims/Party/action owner.
- `java/org/l2jmobius/gameserver/phantoms/party/PhantomAutonomousFarmParty.java` — Party PvE dynamic lifetime.
- `java/org/l2jmobius/gameserver/phantoms/background/PhantomHistoricalBackgroundPlanner.java` — legal target/geo/route; don't duplicate.
- `test/java/org/l2jmobius/tests/phantoms/PhantomLearning001ShadowNative.java`, `PhantomLearning001ActorFixture.java`, `PhantomLearning001Offline.java` — proven disposable TEST techniques, not a permanent general Director.
- `tools/phantom-runtime/Invoke-Learning001Fixture.ps1` — first-party process + SQL + Geo attestation; reuse infrastructure, don't hardcode historical SHA or cycle19.
- `dist/db_installer/sql/game/phantom_learning.sql`, `build.xml`, `dist/game/config/Custom/PhantomPlayers.ini`.
- `java/org/l2jmobius/gameserver/GameServer.java`, `config/ServerConfig.java` — fixed localhost binding.

После первичного аудита составить краткий `DESIGN_DECISIONS.md`: сущности и владельцы состояний, зачем нужны, какие existing classes переиспользованы, какие схемы versioned и как обходится premature success. Не генерировать гигантский «новый AI manager» с дублированием сервиса мира.
