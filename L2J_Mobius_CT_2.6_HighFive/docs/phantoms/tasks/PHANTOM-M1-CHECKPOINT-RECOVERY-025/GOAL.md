# GOAL — восстановить продолжение после checkpoint, не потерять earned state

Required base: `07df36dd7c75e7fcf9d61aee38a6a1dbbf4f09b6`
Branch: `experiment/m1-candidate007-observe008`
Model: GPT-6.1 Sol; reasoning: Very High; без субагентов.

## Продуктовая граница
Настоящие Phantom Player фармят в полном GameServer, переживают arrival/checkpoint,
потом штатно сохраняются и снова загружаются из той же БД. Восстановление после
AFTER_NATIVE не требует Player, который ещё нельзя загрузить из-за pending receipt.

TASK024: A=1/6 и B=0/6; это FAIL, не почти GREEN. Его data-loss counter не доказан:
разница между двумя произвольными samples не равна потере. Использовать sealed native
witness → exact SQL → receipt FINALIZE → controlled restart, без начислений из логов.

## Исправляем в одном пакете
1. Cold VERIFY_PENDING/owned receipt до обычных baseline/goal/presence gates; exact
   transaction recovery и повторный restart. Start point — сохранённый452 на КОПИИ DB024c.
2. Live checkpoint: полный stage/outcome; recoverable отказ до native/DB writes не
   оставляет owner бесхозным SEALED; неизвестный/частичный store не открывает gameplay.
3. Arrival/control continuation не зависит от ordinary ActionLease, которого нет во
   время его собственного checkpoint. После успешного FINALIZE/index/permit rebind
   обычная работа и регистрации native AutoPlay/AutoUse действительно продолжаются.
4. RESERVED earned kill callback не отменяется ради stop: доступная ему очередь должна
   исполняться, checkpoint не блокирует её или нужные ей мониторы. Конкретную причину
   timeout доказать, не объявлять starvation по одному имени ticket.

Точный первый exception зависания110 TASK024 не доказан. Нужны stage + first exception
в коротком synthetic probe; generic catch(false) недостаточен. Combat-flag rejection —
гипотеза из source, не установленная причина этого эпизода.

## Ближайшие обязательные результаты
- В первой части задачи — действительный restart repair452 на новой копии старой БД.
- Не позднее120мин — короткий полный server probe (или точная обоснованная остановка);
  не потратить весь срок только на fixtures.
- Затем минимум две natural scenes, вся cohort, final120 progress, healthy earned drain,
  sameDB restart и повторное native farming. Нельзя убрать из отчёта провалившихся.

Несколько связанных исправлений внутри этого lifecycle прямо разрешены. Не спрашивать
новое разрешение на очередной in-scope subreason. Остановка — safety/outside design,
отсутствие нового evidence после трёх попыток одной гипотезы, или wall-clock budget.
Нет ограничения «4repair rounds» и «8runtime starts», из-за которого нельзя проверить
второй необходимый restart. При этом rerun неизменённого RED без причины запрещён.

Полный срок≤360мин; запас90мин на итоговую проверку/cleanup. После270мин не начинать
новые semantic изменения. Пользовательских input gates нет. Текущие чужие JVM/PLAY
не трогать. Commit + normal push точных своих файлов при любом исходе.
