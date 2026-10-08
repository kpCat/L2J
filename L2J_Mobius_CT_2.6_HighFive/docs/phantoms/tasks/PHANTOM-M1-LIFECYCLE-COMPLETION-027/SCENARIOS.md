# Обязательные проверки (source-level RED и native composition)

| ID | Вход / нарушение на base | Требуемый результат |
|---|---|---|
| S01 | Background operation busy100ms при stock shutdown | Первый prerequisite=PENDING; операция завершилась; materialization/store выполнены до pool stop |
| S02 | Настоящий ON_ATTACKABLE_KILL delay2500ms + stock quest child150ms; shutdown до due | Native body/child once, quiescence before store, нет ручного complete |
| S03 | Background recovery выполняется в scheduled caller; его earned event зависит от scheduled executor | Caller не блокирует собственный progress; завершение через exact control continuation |
| S04 | Real native body throw after writer | Incident сохранён, нет reopen/двойного reward, healthy STOP=false |
| S05 | Callback never runnable / реальный timeout | Monotonic deadline не продлён; classified failure, evidence сохранён |
| S06 | Concurrent/repeated stock shutdown calls | Один attempt, один store, null-instance/no-op semantics сохранены |
| S07 | Query snapshot/POST_STORE во время drain | Нет class/system/owner lock cycle, topology доступна до publication |
| S08 | STARTUP failure cleanup | Не потерять dependency order из task024/025 |
| E01 | EXECUTOR_ENTERED перед блокированным owner lock | Diagnostic отличает dequeue от tryStart; никаких gameplay mutation |
| E02 | stale callback after replacement epoch | No writes/new target/award к replacement |
| E03 | submission rejection | Captured reservation/accounting завершены штатно, first failure сохранён |
| D01 | Real death в materialized native Player | Dead-event order верный, window45s не reset, один native return |
| D02 | Secondary revival раньше own window | Не второй revive и не повтор penalty/store |
| D03 | Restart с canonical DEAD, без old memory/event | Existing recovery принимает, создаёт правильный owner, продолжается native жизнь |
| D04 | Cold DEAD + non-COMPLETE exact catchup | Не обходить NORMAL fence; exact existing recovery contract и guards |
| D05 | Death/cleanup одновременно с shutdown | Новых roots нет; принятая работа сохраняется; no self-drain |
| D06 | Fault после store / до publication | Existing receipt resolver, sameDB restart идемпотентен; no grant/replay |
| L01 | Native return в town, goal у старого фарма | Existing local plan/ownership handoff доводит минимум3 новых farm cycles |
| L02 | Synthetic away/return без hot edits | Soft-dematerialization/background/rematerialization наблюдаемы, никаких pop-in shortcuts |
| P01 | Восемь enrolled IDs: live + уже inactive/dead | Все accounted; final SEALED каждому бывшему live, no-live отдельная native/receipt lineage |
| P02 | SameDB restart два раза | Native values/items/skills exact; допустимые marker transitions отдельно; no receipts replay |

S01 нельзя делать просто unit test предложенной enum. Нужны existing configured
PhantomSystem + real lifecycle composition и latch-controlled accepted operation.
S02 — реальный event/quest path, не Runnable, который только увеличивает AtomicInteger.
D01/D03 — настоящий Player, native death/return, не SQL set HP=0/100 в product clone.
TEST setup в own guarded fixture до lifetime допускается по существующим правилам.
Если base уже PASS конкретного случая, он regression, а не fabricated RED.
