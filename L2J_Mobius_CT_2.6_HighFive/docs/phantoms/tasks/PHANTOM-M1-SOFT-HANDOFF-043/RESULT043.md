# RESULT043 — G4 FAIL, TASK043=PARTIAL_VERIFIED

Полный G4 не достигнут. M1=OPEN, READY_FOR_M2=false, личная приёмка REQUIRED.
Два заранее определённых полных positive attempts C1/D1 исчерпали лимит DESIGN/PLAN.
Следующая задача и другие M1 gates не запускались. Исторический B2 FAIL сохранён.

## Что доказано

- B2 profile367: настоящий INCOMING_COMBAT_HARD_HOLD, HP180→20.058; новые
  cycles/damage после locality expiry не росли, AutoPlay преимущественно paused.
  Exact last acquired root и источник каждого incoming hit — UNKNOWN старого capture.
  Stale pin в B2 не доказан; бой не освобождался насильно. См. `evidence/B2_CAUSE043.json`.
- C1 profile776/object268491397/epoch664878110173300: actual native cycle/reward/damage
  +1, EXP87/SP10. Frozen quiet boundary до departure, штатный пеший уход единственного
  Synthetic observer. Native terminal World/lease/autoSave absent, outstanding/timers0,
  owning save и отсутствие native owner подтверждены.
- C1 фактический background window 77.229855s. Early/late READY state12766 и digest
  совпадают: actual productive committed producer в этом window отсутствует.
  Same profile rematerialized с новой epoch665166368060700, но DEAD и без новой
  полезной native работы. G4=FAIL. Productive calendar и ecology readiness остаются
  гипотезами причины; продуктивная eligible дата сама по себе не producer proof.
- D1 profile1189/object268495017/epoch665774320182300 выбран до outcome по current
  readiness и read-only existing catalogue productive=true на следующие десять минут.
  Actual native earned work и frozen quiet boundary были. Observer дошёл пешком до
  38131,42673,-3616. SOFT_RETIRE_BOUND120=FAIL; background/return не достигнуты.
- Последний D1 away frame666113459201100: observer RUNNING на away endpoint,
  same native epoch, HP200, human age129.8422818s, physical/nativeVisible=false,
  retentionPins=[], IDLE, native outstanding0, без attack/cast/move/inCombat и без
  visible incoming targets в bounded scope2000. Scheduler ACTIVE/STABLE, один сигнал.
  Это outcome RED / STALE_HOLD_AFTER_SETTLEMENT_CANDIDATE, но source key/expiry сигнала
  отсутствуют. Native reads NONATOMIC_VOLATILE. Causal RED конкретного product owner
  **не доказан**, поэтому production fix не внесён. См. `evidence/D1-LIFE043.json`.

Не смешивать D1 away frames с поздними finally/fallback кадрами после Synthetic STOPPED:
они не подтверждают условия исходного ухода. Другой actor, BACKGROUND flag, elapsed
sleep или productive forecast не засчитывались как G4 PASS.

## Исходные ошибки и ограничение попыток

| Run | Результат | Полная positive попытка |
|---|---|---|
| A1 | Native189 earned; private predeparture guard ошибочно запрещал sole observer NATIVE_VISIBLE. До MOVE остановлен | Нет |
| A2 | Сохранён тот же189; законная DEAD identity, исходные living preconditions не выполнены. До MOVE остановлен | Нет |
| B1 | Предопределённый living native caster отсутствовал в fixed enrollment. До MOVE остановлен | Нет |
| C1 | Первый full measurement; lawful release, но нет productive background и useful same-profile return | 1, FAIL |
| D1 | Второй full measurement после prospective productive input discrimination; soft-retire120 FAIL | 2, FAIL |

Private guard исправлен только после actual A1 RED: same-frame GREEN и negatives
stale epoch / foreign native hold / incoming combat PASS. Production guard не менялся.
Промежуточный audit189 без actual TRANSACTION_COMMIT039 был отвергнут; V2 проверил
134 owning records, но normal-stop/next-boot background189 не объявлялся G4.

## Conservation043 и проверки

| Closed normal run | Native births | Owning commits | FULL_SAVE / GLOBAL_DRAIN / closed RUN_END |
|---|---:|---:|---|
| A1 | 41 | 3037 | PASS |
| A2 | 26 | 2376 | PASS |
| B1 | 147 | 11404 | PASS |
| C1 | 1027 | 110870 | PASS |
| D1, final | 982 | 80260 | PASS |

Все births/terminals и actual native fields/items/skills/context/goal/PNC проверены
existing strict039 validator, stopped SQL; global COMPLETE/retained0 до pool stop,
RUN_END/dropped0. Полные proof JSON/ledger/witness archives остаются local по exact
path/SHA в `evidence/CONSERVATION_INDEX043.json`; компактный index не заменяет raw.
P4 PASS относится только к own043, не закрывает исторические042 gaps или M1 G3.

Current jar-first matrix93/93 (18 routes), lifecycle11/11, focused soft-return3/3 PASS.
Existing conservation verifier negatives26/26 PASS. WrongManifest / MixedOwners
отказаны до DB init; wrong source Start — до runtime mutation. Actual TEST owner RED
на неподдерживаемом043a namespace → minimal local041/042 pattern → GREEN3/3.
Команды/exit/JAR provenance сохранены; см. `evidence/REGRESSIONS043.json` и `Verify043.ps1`.

GameServer.jar SHA256=`5c4ae8bfff0568ad2fd204f6c3be79268d03c55ed95862c5b24619ad3719e98c`.
NO_PRODUCT_EDITS: Java product/config/data/evaluator028 не изменялись, runtime JAR
не пересобирался. G2 exact same candidate reuse: A5 8/8 + independent C24 8/8 PASS,
hashes и пустые semantic/current product diffs проверены в `evidence/G2_REUSE043.json`.

## Среда, scope и доставка

Использован только High Five OWN, новая043a schema `l2jmobiush5_phantom_test_043a`
из immutable036 export, ports7795/2125/9035. Импорт ровно один, дальнейшего reset/reimport
не было.043b не создан. Собственные JVM штатно остановлены; DB/raw сохранены.
Protected040a PID18688/30312 и исходные start ticks/runtime exact;304 исходных файлов036
и B2 raw SHA неизменны. Это bounded preservation check, не новый snapshot чужих DB.
MAIN/PLAY/final delivery/client/общая MariaDB service не менялись; чужих schema writes нет.
45/90/120, Synthetic525, rates/radii, earned/debt/CAS и stock rewards не менялись.

Единственный Java source edit — TEST
`test/java/org/l2jmobius/tests/phantoms/PhantomHeadlessPlayerTestEnvironment.java`.
Прочие поставляемые изменения — bounded043 reports/helpers/proposals в этом task family.
Private ops043/contract043a/raw/DB/JAR/secrets в commit и ZIP не включаются.
Bounded exception >10 paths: один043 artifact family и один ближайший TEST owner;
другие subsystem/artifact families не редактировались.

P6 delivery PASS: первый actual exact-path ordinary commit/non-force push
`0175d1bbd5e2038c347a4fa1297975238fdc4dfc`, HEAD=remote, tracked clean подтверждены.
Exact-path self-review/allowlist, CRC/all-member SHA/no duplicate/no traversal PASS.
Final receipt
`DELIVERY_AUDIT043.json` создаётся после третьего audit-only normal push и отдельно
фиксирует final HEAD=remote. Исходный second-push DELIVERY043.json сохранён.
Publication receipts не превращают P2/P3 в PASS. См. `GATES043.tsv`, `REVIEW043.md`.

Git использовался по прямому `/goal` и TASK/HANDOFF043 для input/scope/diff verification
и exact-path ordinary commit/non-force push. Точные publication argv —
`GIT_COMMANDS043.json`, `DELIVERY043.json` и final `DELIVERY_AUDIT043.json`;
read-only inventory — `GIT_INSPECTION043.md`.
Ни branch mutation, reset, restore, clean, merge, rebase, amend, force, broad add не было.

mojibake-маркеры в изменённых файлах проверены: PASS, см. `evidence/ENCODING043.json`.
escaped Cyrillic в изменённых файлах проверены: PASS, отдельный итог в том же документе.

## Оставшаяся точная граница

Для адресного продолжения после личного review нужны source key/sequence/expiry
ACTIVE-сигнала1189 и связь с owner decision на settled boundary. Read-first кандидаты:
existing population schedule/ecology permission edge и background native-context demand
(это гипотезы, не доказанная виновность). Третья native positive попытка или новый owner
fix без causal proof в043 не разрешены. Ни следующая задача, ни другие gates не начаты.
