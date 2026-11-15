# Минимальная поправка плана015 — на согласование

Финальное обновление: после подготовки варианта2 владелец отдельно утвердил PARTY_MP_POLICY_PLAN.md и одну общую MP policy дельту. Она реализована; результат PARTIAL015 описан в RESULT.md. Ниже сохранена история предложений до этого approval; диагностический вариант не выбран как решение, старые OWNER_APPROVAL_REQUIRED/NOT_AUTHORIZED относятся к тому этапу.

Статус: `OWNER_APPROVAL_REQUIRED`. Основание — read-only REALISM_AUDIT.md и последнее уточнение владельца. Вариант A ранее принят; полезная работа, история и бюджеты сохраняются. Новый Goal не создаётся.

## Предлагаемая поправка в текущем scope

1. Сохранить общий canonical observer, точную арифметику/normal Party parity tests, stat/source/owner guards и отдельный bounded budget015. Не подключать прототип `previewRecoveryMp` как новый запрет начинать бой.
2. В диагностике разделить три факта: `actionReady` — существующая native eligibility текущего действия; `partyPolicyReady` — результат действующего Party admission; `mpRisk` — прогноз ресурса целого боя. Если факт не получен от своего существующего owner, он UNKNOWN. От total3060/max2806 нельзя вывести actionReady.
3. Называть текущую величину `nativePolicyMpEstimate`, не R_min реального боя. Переименовать экспериментальный `PROVEN_IMPOSSIBLE` в результат о текущей policy. Сам по себе прогноз нехватки MP не создаёт новый class/skill/scene blacklist или early negative об игровой законности.
4. В TRAIN receipts фиксировать реальные current/max MP, actual native regen, grade/наличие Soulshots/Blessed, buff remaining и реальные support actors/готовность. Поддержку, питомца, recharge, shots и оплаченный supply не выводить из одного capability flag. Не давать фиктивные будущие credits и не выдавать предметы.
5. Действующий production admission, native resource/target/Geo/LOS/owner checks, rewards и pre-CUT recheck сохраняются. Если этот unchanged policy блокирует G3, отчёт называет именно policy blocker. Не выдавать его за физическую невозможность/необучаемость профессии и не продлевать ожидания.

Поправка не реализует новые production требования20+ shots/Blessed, subsidy policy и смену стратегии после трёх смертей. Они фиксируются обязательными незакрытыми требованиями целевой архитектуры. Новых предметов/актеров/питомцев/БД или новых file families ради них в015 не добавляется.

## Ограничение результата

Это минимальная поправка смысла и применения read-only контракта. Она не исправляет сам существующий избыточно консервативный MP hard gate. При неизменном production-поведении нельзя обещать, что рискованный Party candidate теперь будет допущен. Full015 по-прежнему требует фактического G0–G8; факт тестов/диагностики его не заменяет.

Если владельцу нужно изменить именно поведение уже в015, необходимо отдельное явное разрешение на **одну MP policy правку**: рассматривать недостаточный прогноз full-fight MP как риск, сохранив проверки текущего native действия и остальные native gates. Это меняет прежнее требование «production-поведение сохраняется» и сейчас не разрешено. Конкретный код такого изменения будет представлен отдельно до записи; mandatory shots/economy/death strategy не присоединяются к нему автоматически.

## Конкретный альтернативный вариант — одна MP policy правка

Статус этого варианта: `NOT_AUTHORIZED`. Это дополнение к уже направленному выбору, не новая задача и не реализация. Исходное требование сохранения production не позволяет выполнить его без явного решения владельца.

Точное место поведения — `java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java:748` в SOURCE004: после общего расчёта `actions * costs[index]` сейчас `partyMpBudgetExceeded(..., player.getCurrentMp())` выдаёт `WAIT_GROUP_MP` и возвращает false. Минимальная поведенческая дельта — сохранить этот расчёт для MP-risk receipt, но убрать **только его право вернуть false**. Не повышать порог80%, не подставлять дешёвый skill, MP recharge, shots multiplier или maximumMP вместо наблюдаемого ресурса. Не превращать risk в refusal через существующий witness channel.

Этот общий метод вызывается первоначальным выбором цели (`:554`) и повторной проверкой encounter (`:1128`, continuing=true). Изменение должно быть единым в обоих путях; TEST-only bypass или отличающаяся A/B семантика не допускаются. Ранний preview по-прежнему не выдаёт `PROVEN_IMPOSSIBLE` на основании full-fight MP forecast.

Что остаётся на своих местах:

- owner/Party/source/instance/level-band и native target/Geo/LOS/подход;
- learned skills, фактическая готовность capability, отключённые навыки, native текущие MP/HP/item/weapon/status/reuse/target conditions;
- существующие DPS/duration, ammunition, угрозы, готовность heal и прогноз HP-risk;
- реальное исполнение и расход MP в native cast, поддержка/баффы, recovery, rewards, смерть и cleanup.

Это перечень сохранённых проверок, **не доказательство достаточности всех существующих risk policies**. Например, ограничение duration60сек и HP-risk остаются консервативными, а death-context history пока отсутствует. Нельзя объявить завершённой целевую архитектуру реализма одной такой правкой.

Проверенные native границы: `PhantomAutonomousFarmParty.java:1064,1076–1078` сохраняют real readyNow для damage contributor; backend `:636–638` фильтрует навыки через существующие native conditions при первоначальном admission. `combat/L2jCombatBackend.java:1465–1504` перед dispatch снова получает настоящий learned skill и вызывает `checkDoCastConditions`; `model/actor/Creature.java:2236–2246` требует MP для текущего каста. Эти пути не меняются данным вариантом. Это не выдаёт permission одному конкретному будущему действию: его условия проверяются при исполнении.

Точный дополнительный code scope этого варианта — **поведенческое исключение только для названной MP-ветки в уже разрешённом backend файле**. Сопутствующие изменения только в уже разрешённых `AutonomousTrainingChecks.java`, `PhantomPartySuite.java`, `PhantomAutonomousTrainingNative.java` и существующем focused Ant target: исправить семантику экспериментального результата/тестов и сохранить truthful receipt. Новых production файлов, config flags, классов/skills blacklist, Solo-policy, supply/subsidy, pet или death-strategy изменений этот вариант не разрешает.

Проверки после отдельного утверждения:

1. RED на действующем canonical Party path: MP estimate3060 при actual max2806 не должен быть самостоятельным admission refusal при готовом законном текущем действии. Затем GREEN после ровно названной поведенческой дельты. Тест одной арифметической функции и MemoryBackend с заранее заданным true этого не доказывают; fixture должен проходить реальный изменённый production path. Способ DB-free fixture ещё не подтверждён и не считается готовым доказательством.
2. Отдельно доказать сохранение native отказа при нехватке MP на текущий cast, и неизменность готовой Party без forecast shortfall. Не заполнять ресурсы тестируемого native актёра ради live admission; DB-free fixture не выдаётся за native TRAIN evidence.
3. Существующие Party/support/headless deadline tests плюс точный scope guard. Не ожидать побайтового сохранения MP admission результата: именно эта одна дельта требует разрешения; все прочие ветки должны сохраняться.
4. Проверить условия G1/G2 до расходования readiness. При недоказанном G3 не запускать GS. Существующие лимиты0/2 readiness,0/3 GS,0/1 FIT остаются прежними; изменение policy не является доказательством TRAIN cohort, способности победить или улучшения модели.

Последствие выбора: можно устранить отказ, вызванный **только** full-fight MP forecast, но возрастает вероятность реального истощения MP и поражения. Никакой будущий recharge или pet не гарантируется. Если другой unchanged gate блокирует cohort, нужен его точный диагноз в015, а не автоматическое разрешение следующего production изменения.

## Последовательность после утверждения этой поправки

- Обновить IMPLEMENTATION_PLAN/G1–G2 interpretation в пределах текущего015; сохранить immutable историю первоначального замысла и RED→GREEN evidence.
- Перенастроить только экспериментальные diagnostics/tests под разделение action readiness, policy refusal и риска; не добавлять отказа на основании одного forecast.
- Проверить unchanged production Party и точный scope. Перед любым live этапом повторно проверить, выполнены ли исходные budget/admission условия с учётом согласованного плана; не запускать readiness ради ожидания при недоказанной осуществимости.
- Продолжить015 в разрешённых пределах либо подготовить честный PARTIAL с точным policy/contract диагнозом, scoped feature publication и независимым readback. Финальный owner gate остаётся REQUIRED.

До решения владельца — только read-only аудит и документация. Source work015 не откатывается, commit/push не выполняются, live counters остаются прежними.
