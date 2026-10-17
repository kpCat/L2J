# SOURCE_MAP059 — конкретная исходная точка

Проверена удалённая ветка `experiment/m1-candidate007-observe008`: **8a1b3e5873cfc5a9dbcb94fc2c11db0d277ba268**. Код последней функциональной сборки058: **0cf254f82f22806be7d9a3609098c6080225d347**, Game SHA **BD54E50CEB9749DFD830B0C7514C665D846D085D8C0DD2C91D1E2617CC94402D** по опубликованному RESULT058. Автор пакета прочитал репозиторий, но не запускал сервер/тесты пользователя и не проверял состояние Windows/БД059.

Все пути ниже от `L2J_Mobius_CT_2.6_HighFive`. BASE URLs: `https://github.com/kpCat/L2J/blob/8a1b3e5873cfc5a9dbcb94fc2c11db0d277ba268/L2J_Mobius_CT_2.6_HighFive/` + exact path.

## Что подтверждено чтением перед выдачей
| Файл / прочитанный участок | Факт, а не предположение |
|---|---|
| `docs/phantoms/reports/PHANTOM-M2-M7-CAMPAIGN-058.md` | Native М2 принят; М3 «стой здесь» не поддержан; SP до сохранения конфликтует; raid только subset, Kamaloka/Rift encounters не приняты; две release-сборки нельзя смешивать |
| `docs/phantoms/tasks/PHANTOM-M2-M7-CAMPAIGN-058/ACCEPTANCE.md` и предыдущий ZIP058 | S3…S7 и server/owner различаются; новое задание не превращает fixtures в natural career |
| `java/org/l2jmobius/gameserver/phantoms/progression/PhantomClassSkillLearningTransaction.java:29–179` | `execute`/`executeLocked`/`lockAndValidate`; SQL FOR UPDATE, optimistic SP/skill/item checks, commit затем runtime reconciliation. Разный durable/runtime SP даёт DURABLE_SP_STATE_CONFLICT |
| `java/org/l2jmobius/gameserver/phantoms/progression/L2jProgressionBackend.java:115–147` | Backend владеет existing `PhantomClassSkillLearningTransaction`; loader copied catalog ≠ полный аудит всех SP writers |
| `java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationExecutionPort.java:121–191` | `prepareGoal` уже маршрутизирует invite/leave/travel/accept по catalog и party generation; default rejected. M3 новые intent нельзя оставлять только текстом |
| `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java:35–205,300–485` | Shared route/owned movement и per-follower state существуют; cancelFollow, deadline/identity, caster busy и native movement lease проверяются. Повторный follower engine не нужен |
| `java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyFollowPolicy.java` | Immutable bounds; defaults start350/stop250/replan64/waypoint64/max6000/timeout30000ms; новый HOLD не путать с уже существующей follow distance |
| `tools/phantom-runtime/RuntimeCampaign058Guard.java` | Exact058a/release058a root/schema/owner и2138/7838/9138. Новый059 output нельзя молча провести через старый mapping; explicit case с negatives разрешён AUTHORITY |

SP comparison доказан кодом; корректный способ согласования всех concurrent writers ещё требует causal анализа. Отчёт058 доказывает конкретные recorded scenarios, не все возможные состояния. Hypothesis о выдаче sync/save boundary не является готовым проверенным патчем.

## Initial READ_SET ≤12 product/test файлов
Пакет/локальные правила/RESULT058 читать дополнительно; не грузить весь репозиторий.
1. `phantoms/conversation/L2jPhantomConversationExecutionPort.java` — prepareGoal/support delivery/current authority.
2. `phantoms/conversation/PhantomConversationService.java` — addressed speaker/cooldown/outcome.
3. `phantoms/party/PhantomPartyCoordinator.java` — current real-led reconcile, commands/claims, leave/leader/lifecycle.
4. `phantoms/party/PhantomPartyRouteCoordinator.java` — follow/cancel/movement lease.
5. `phantoms/party/model/PhantomPartyModel.java` — existing state/generation/persistence carriers.
6. `phantoms/progression/PhantomClassSkillLearningTransaction.java` — execute, validation, after-commit, runtime state.
7. `phantoms/progression/L2jProgressionBackend.java` — actual learn caller, target/trainer/readiness and current rank.
8. `test/java/org/l2jmobius/tests/phantoms/PhantomConversationIntegrationSuite.java` — ближайшая native composition.
9. `test/java/org/l2jmobius/tests/phantoms/PhantomSocialMemory058.java` — counterpart/restart pattern.
10. `test/java/org/l2jmobius/tests/phantoms/PhantomCombatServerIntegrationSuite.java` — только earned/paid058 и нужные role symbols.
11. `test/java/org/l2jmobius/tests/phantoms/PhantomTestLauncher.java` — exact modes/seeds.
12. `build.xml` — только нужные compile/test/package targets.

Пути1–7 сокращены от `java/org/l2jmobius/gameserver/`. Не считать все их тела заранее отревьюенными. Следующие точные codec/store/XML/SQL/callback writers читать по вызовам; ограничение12 не запрещает нужное чтение и не является поводом STOP.

## Переиспользовать, не копировать
- `test/.../PhantomCampaign058DatabaseLane.java`, `PhantomCampaign058PersistentLane.java`, `PhantomCampaign058SceneSetup.java`, `PhantomCompanionComposition058.java`, `PhantomHeadlessPlayerTestEnvironment.java` — actual existing lanes/setup. Штатный fixture helper не запускать на cumulative или release schema.
- Сохранённый `.phantom-local/contract058a/final-selected058b.tsv` и corresponding own058 logs — exact команды прошлых101 cases; наличие проверить локально. Новые raw идут в059 dirs, не поверх058.
- `tools/phantom-runtime/Build-Package.ps1`, `Runtime.ps1`, `Campaign058Ownership.ps1`, `RuntimeCampaign058Guard.java`, `RuntimeDatabaseCheck.java`, `Export-Campaign058.ps1` — обычная поставка; минимальные current058/новый059 extensions.
- `dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml`, `high-five-ru-corpus-v1.tsv`; `conversation/high-five-ru-conversation-v1.xml` и execution catalog; `party/high-five-party-follow-v1.xml` — совместное parser/content покрытие новых команд.

## Поздние этапы — адресно после первых результатов
М4: existing `phantoms/pvp`, affiliations/clans, `PhantomPvpSuite`, actual PlayerStatus damage wiring. М5: existing `phantoms/raid/L2jPhantomRaidAttemptRuntime`, Readiness/Assembly и PartyTactics; native raid058 modes. М6: existing `phantoms/rift`/quest-instance и реальные scripts template57/type1, не guessed bypass. М7: progression/SkillTreeData/village_master/native quests, journal/save writers для SP. Найти exact file по imports/символам, не придумывать API.

Не использовать сентябрьский FEATURE_COMPLETE marker как принятие новых игровых milestones. Не утверждать, что все test seeds/все scripts прочитаны автором пакета. Live local сведения берёт исполнитель из exact owner058, не из прежнего скриншота.
