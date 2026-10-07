# Решение: один Player, штатная механика, две разные границы владения

## 1. ИИ и цель
Goal farm.background означает «заниматься подходящим фармом в локальной зоне», не
«стоять навечно возле единственного objectId». Persisted npcId/anchor — текущий план;
конкретный NPC objectId/spawnGeneration — временная цель native AutoPlay.
Сохранить существующие Decision/VisibleAutoPlay/AutoUse, не писать второй combat engine.

M1 policy: выбрать допустимую локальную цель, атаковать/кастовать, подобрать законный loot,
перейти к следующей. Если цель исчезла/недоступна — clear/reselect по stock; после bounded
неудачи — existing local replan. Не сбрасывать goal/history/sensor в каждом tick.
Fighter использует native melee; mage — доступные offensive skills/MP/range. Восстановление
MP/HP допустимо при фактическом росте ресурса, но не бесконечный self-heal с fullHP.
Предпочитать свободную цель при равных условиях через existing respectful/selection policy,
но НЕ запрещать пересекающиеся бои, чтобы спрятать defect multi-recipient.
Позиция/LOS/native path/range/cooldown законны по Mobius. Без teleport Phantom на глазах,
без изменения water guard или увеличения дальности ради теста. Дальний travel — background.
Новая utility-функция, если нужна, малая; лучник/kite/summoner/party-тактика — не новый M1 gate.

## 2. Почему нужен другой контракт динамических участников
Сохранить строгое правило: уже опубликованная delayed работа не меняет захваченный
Player reference/owner/epoch и не пишет после закрытия этого lifetime.
Но другое правило «состав всех будущих получателей известен ещё до начала каста» неверно.
Обычный второй атакующий или изменение законного recipient не тождественны stale lifetime.

Разделить:
A. DELAYED_RESUME — воспроизведение ранее захваченного набора. Только exact captured
   owners/tickets. Любая подмена/закрытый owner без retained earned ticket — REJECT.
B. FRESH_NATIVE_WRITE_BOUNDARY — native-механика определила актуальных участников новой
   подоперации ДО её первого HP/EXP/SP/item writer. Уже захваченные остаются exact;
   новый явно определённый current OPEN owner может быть enrolled через обычный reserve
   (parent=null), до writer. SEALED/DETACHED/foreign/failed нельзя заимствовать.

Общий алгоритм в existing PlayerNativeWork, а не отдельный engine:
1) собрать immutable exact Participant(playerRef, ownerRef, epoch, role) с дедупликацией;
   отдельно отметить original captured и new explicit. Никакого поиска замены по charId.
2) reserve всех участников до действий; existing exact parent → child, новый OPEN → root;
   не держать monitor первого owner при обращении к следующему, не ждать чужой drain.
3) при отказе освободить только свои ещё неопубликованные reservations; ни одного writer;
   если уже была заработана/опубликована обязанность, её нельзя объявлять отменённой.
4) tryStart/revalidate ВСЕ reservations; затем установить scopes, выполнить один stock body;
   cleanup contexts в обратном порядке, tickets complete ровно один раз и на exception.
5) каждый downstream delayed callback получает этот immutable набор до publication.
Один и тот же frozen native roster должен участвовать и в admission, и в writer. Нельзя
reserve по snapshotA, а затем повторно прочитать party/aggro и написать snapshotB. В месте,
где stock API уже принимает rewardedMembers/targets, передать тот же захваченный список;
если stock вычисляет recipients внутри — boundary ставится после этого вычисления, но
перед первым drop/HP/EXP/SP writer. Calculations и eligibility не дублировать в Phantom.
Retry подоперации не запускает заново уже оплаченный cast/расход MP/items. Реальный отказ
после начала writer не объявлять pre-write: no replay, retain incident и безопасный stop.

Не превращать любую функцию, вызванную из EARNED, в FRESH boundary. Fresh enrollment
доступен только явно названным точкам до native effect/reward writer. Прочие run/schedule
сохраняют строгий delayed contract. Proposal AdmissionDecision023.java кодирует таблицу,
а не выполняет enroll и не предоставляет привилегии автоматически.

## 3. Где провести границу
Предпочтение: original actor/direct targets фиксируются при publication. Дополнительные
reward-only агро/party participants определяются и резервируются у native reward boundary,
до doItemDrop/addExpAndSp/distributeXpAndSp. Transfer/direct effects — перед своим HP
writer. Не заставлять каждый cast-hit заимствовать весь динамический aggro-list без роли.
Переиспользовать существующие capture/reserve/ParticipantWork; только точечные hooks
Attackable/Party/Player/Creature из SOURCE_MAP при подтверждённой необходимости.
Нельзя просто удалить requireCapturedEarnedParticipants или игнорировать damage>1.

Сохранить штатные recipients/rates/formulas/loot restrictions, no double reward.
Mixed REAL-like + Phantom и два естественных attackers обязаны работать. If new recipient
успел закрыться до linearized admission, нельзя писать в него. Отказ pre-write должен
безопасно завершать/отменять текущую ещё не заработанную подоперацию и native cast state,
не навечно ломая здоровых соседей. Уже earned obligations завершаются штатно до store.
Не скрывать реальную writer exception, не очищать _failure и не разблокировать неопределённый
snapshot. Если нет доказанной before-writer границы — это unresolved safety blocker.

## 4. Сессия/диагностика
Сохранить исправления022 temporary PAUSED vs REVOKED и exact session/epoch checks.
Истина: live registry+последний completed tick+native counters, а не stale runtimeReason.
Evidence — наблюдение, не источник наград. Overflow остаётся sticky, но ограниченное
кольцо технических trace-сообщений может перезаписываться отдельно от scalar counters.
Не увеличивать120s/16-target пределы для сокрытия leak. Правильно завершить episode/target
attribution после полезного прогресса. Catch-up и visible не пишут одни rewards одновременно.

## 5. Lifecycle как часть этой задачи
После обычного завершившегося боя: close new roots → finish earned children → retire
cancelable timers → SEALED → immutable store PREPARE/native/FINALIZE → World delete →
release exact identity. No SELF_DRAIN: не ждать ticket, в котором выполняется cleanup;
публиковать существующую control continuation на boundary вне native work context.
Нельзя освобождать identity раньше earned writer. Faulted uncertain actors не считать
успешно сохранёнными. Исправление prevention/finalization обычных отказов должно устранять
пачку retained8 в нормальном run, не маскировать её удалением entries.
Native death/recovery, local stuck и return/reentry включены. Новые данные/схема не нужны.

## 6. Наблюдатель — переиспользование, а не новая сущность мира
Ночью: LocalPlaySyntheticHumanSession с cloned TestAdmin, identity LOCALPLAY_TEST_HUMAN.
Утром: настоящий GameClient даёт REAL_LOGIN; existing Pilot autoattach выдаёт transport
permission TestAdmin. Системы воспринимают фактическую native world position каждого.
Не добавлять душу/possessor, fake client или единственный глобальный якорь на всю population.
QoL AllowedAccounts не изменять. Право управлять observer не равно праву считаться REAL.
