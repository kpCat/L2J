# Source map — проверено по GitHub c5b81f89 (2026-11-10)
Пути относительно модуля `L2J_Mobius_CT_2.6_HighFive`.

- `java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java`:
  `issueAction(PhantomCombatSession,ActorSnapshot)` — обычный Solo и Party-owned PvE skill selection (around lines 1765–1860). Сейчас берёт `selectedSkills().get(session._nextSkill++ % size)`. Идеальный минимальный seam для нейроскоринга 2–4 lawful skills. НЕ менять `issuePvpAction` или raid/siege special handlers в LEARN-001. `processLoot` обязателен и остаётся как есть.
- `combat/PhantomCombatSession.java`: exact actor/target/session generations; bounded `_nextSkill`, `_ownedAction`, `_actorLease`. Не использовать stale session as training event.
- `combat/PhantomCombatCapabilityResolver.java` — список штатных изученных и доступных SelectedSkill (max 4). Learner не добавляет навыки.
- `combat/PhantomCombatBackend.java`, `combat/L2jCombatBackend.java` — action outcomes, immutable/native actor/target snapshots; использовать только подтверждённые атрибутируемые события.
- `java/org/l2jmobius/gameserver/model/actor/PlayerNativeEvidence.java`: `TargetReceipts`, `damageSequence`, `combatDamage`/target evidence, HP/MP/reward snapshots. **Агрегатная HP разница чужой Party НЕ доказательство damage конкретного skill**. Разобрать native ownership before writing reward updater.
- `party/PhantomPartyCoordinator.java`: `dispatchTacticalCombat`, `requestAutonomousFarm`, native scopes and ownership, calls existing CombatService. Group actions, Heal/resurrection managed outside chosen skill seam.
- `party/PhantomPartyTactics.java`: `plan` sorts fixed priorities (HEAL_MEMBER/RESURRECT_MEMBER/ASSIST/etc), untouched first version. Future task may add learned tactical intent selection.
- `decision/PhantomUtilitySelector.java`: evaluates `PhantomDecisionCandidate` with weighted consideration average, 0..1000 score. Это planning utility; **не подменять им Combat skill inference**. In future a trained-score port can feed this selector too.
- `decision/PhantomDecisionEngine.java`: installs `_selector`, handles plan ownership; не делать глобальный redesign.
- `PhantomSystem.java`: binds `_combatService`, `_partyCoordinator`, goal execution, lifecycle, config. Однократная установка shared `LearningPolicyPort`, immutable model.
- `profile/PhantomProfileRepository.java`: per-profile components with versioned CAS (не использовать для общей модели). Глобальные learning-models/examples хранить отдельно, в installer game schema.
- `dist/db_installer/sql/game/` — текущие штатные SQL скрипты, включая `phantom_profiles.sql`, `phantom_reservations.sql`. Добавлять learning-specific schema без удаления/перезаписи существующих.
- `dist/game/config/Custom/PhantomPlayers.ini` и соответствующий Config loader — текущая модель настроек, read before edit.
- `test/java/org/l2jmobius/tests/phantoms/` — существующий test registry, `PhantomPartySuite`, `PhantomHeadlessPlayerTestEnvironment`, `PhantomWorldCoordination130Suite`, combat action ownership suite. Использовать быстрый test harness.
- `tools/phantom-runtime/` — G0, test-only launcher, STOP and postflight; не копировать огромный TASK130 runner.
- `docs/phantoms/tasks/PHANTOM-WORLD-COORDINATION-130/continuations/PARTY-PVE-GROUP-CONTRACT-20261108/HANDOFF.md` — история TASK130; читать точечно, не перепроходить native gate.
