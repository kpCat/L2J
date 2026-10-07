# Acceptance-oriented сценарии (не «тесты ради тестов»)

## Правила fixtures
В guarded TEST допускается заранее создать законный класс/уровень/экипировку через
existing fixture и stock skill tree, до baseline, с точным restore. Это не final natural
acceptance. Final scenes используют обычные clone-профили без ручной выдачи ресурсов,
без отключения NPC AI и без подсовывания собственных decision work items.

## N: точная native механика, guarded fixtures
N01 — solo mage5cycles и fighter5cycles, stock NPC AI ON, реальные callbacks/EXP/SP/loot.
N02 — 3 Player бьют одного живого моба со сдвинутыми cast/attack timings; второй наносит
настоящий damage после публикации первого каста. Все продолжают следующие бои, нет
NATIVE_EARNED_RECIPIENT_NOT_CAPTURED/вечного CAST/DRAINING. Сверка наград со stock controls.
N03 — новый lawful OPEN recipient допускается fresh boundary; тот же сценарий при SEALED,
DETACHED, changed epoch и same charId/replaced Player не пишет в старый/новый чужой owner.
N04 — no partial writer: admission denied до native body, reservations rollback; published
EARNED не cancelled; after-start failure сохраняет incident/не повторяет награду.
N05 — mixed ordinary non-Phantom + Phantom в тех же native pools; pause/revoke одного не
ломает остальных. Ordinary контроль без fake REAL_LOGIN.
N06 — actual target умер/исчез между launch/hit; callback завершён, нет вечного CAST;
выбор следующей цели. Stale старый callback не abort новый cast.
N07 — Tutorial missing state + normal STARTED-state; никакого fake tutorial/reset.
N08 — late reward after target switch корректно учитывается, cap/time reversal/overflow
сохранены; наблюдатель не присваивает награды и не влияет на механику.
N09 — temporary checkpoint PAUSED сохраняет регистрации; permanent revoke снимает точные
регистрации, старый session callback не получает lease новой сессии.
N10 — dynamic/secondary suite: OPEN позитивный, sealed негативный, реальная ordinary
transfer/party baseline. Предварительно провалившаяся fixture не считается проверкой guard.

## L: обычная жизнь и сохранение
L01 — завершение native cast/reward одновременно с shutdown: no self-drain, wait earned,
не wait own ticket, no double completion, exact store/no retained entries.
L02 — смерть native NPC damage, corpse window, existing recovery/revive, снова native farm.
L03 — недоступная/водная локальная цель: bounded reject → другой local plan; повтор того же
terminal не считается прогрессом; два разных препятствия на одной revision проверены.
L04 — MP/HP deficit: реальные resource deltas и cooldown, bounded rest → offense;
fullHP selfheal без полезного результата не принимается как farm.
L05 — observer native walk away: no abrupt disappearance при наблюдаемости → eventual
soft demat; background advances only under its owner; walk back → remat/native actions.
L06 — graceful restart на той же DB после earned progress: exp/sp/items/goal receipt
сохранены; no duplicate Player/world/identity; снова фарм.
L07 — crash windows owned store PREPARE/native/FINALIZE: existing faults на guarded TEST,
плюс один real process crash на отдельной owned clone. Recovery не создаёт duplicate
reward и не скрывает retained uncertain state. Не считать восстановление from export recovery.

## S: полно-серверный unattended proof
S01 setup без клиента: LOCALPLAY_TEST_HUMAN, GameClient null; read-only snapshots честные.
S02 SceneA: frozen production build,360–420s, минимум4 natural eligible fighters/mages.
Первые2 primary фиксируются ДО действий; каждый>=5completed farm cycles + EXP/SP.
Вся cohort сохраняется, остальные не меньше3 reward-bearing target transitions с damage;
нет silent fault/вечного idle. Убитых мобов считать уникально по instance/object/spawnGeneration.
S03 SceneB: тот же SHA/config, другая natural первичная пара и локальная spawn area;
те же thresholds, без producer fix между A/B. Не выбирать только потом успешных.
S04 native leave/return, bounded local stuck/death и restart: L02/L03/L05/L06.
S05 full graceful stop: Phantom retained=0, pending owned work=0, no identity/World leaks;
PIDs0 не замена этим assertions. Сверка сохранённых final native values/receipts.
S06 repeated startup после S05 на тех же данных, обычные actors снова действуют.

Если недостаточно natural candidates/целей для frozen scene: фиксировать ENVIRONMENT_GAP,
не управлять ботами и не подставлять успех TEST. Разрешён один детерминированный переход
observer в другой factual anchor до baseline, не гонка на удачу.

В natural farm scenes каждый actor должен иметь хотя бы один reward-bearing target
transition в ПОСЛЕДНИЕ120s. Ранний burst и затем минуты idle не являются continuous PASS.
Отдельный death/long-rest сценарий проверяется отдельно, не переименовывается в farm PASS.
