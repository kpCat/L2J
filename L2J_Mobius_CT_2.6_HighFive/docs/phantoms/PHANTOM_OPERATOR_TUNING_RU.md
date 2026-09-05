# Phantom World: настройка оператором

Этот документ описывает только ключи, которые реально читает `PhantomPlayersConfig`. Shipped-конфиг остаётся выключенным: `EnablePhantomSystem=False`, `PhantomPopulationTarget=0`, `PhantomPopulationActiveTarget=0`. Local-play preset находится в `docs/phantoms/examples/PhantomPlayers.local-play.ini`.

## Полный список текущих настроек

| Настройка | Тип | Что меняет | Safe local-play range | Нужен restart | Риск |
|---|---|---|---|---|---|
| `EnablePhantomSystem` | boolean | Разрешает запуск Phantom runtime | `False`; `True` только после preflight и явного применения preset | Да | При включении создаются durable Phantom identities |
| `EnablePhantomDiagnostics` | boolean | Включает bounded trace/diagnostics | `False` обычно, `True` для локальной диагностики | Да | Дополнительные CPU/память и диагностические данные |
| `MaxMaterializedPhantoms` | int | Верхняя граница одновременно materialized `Player` | `8..64`, preset `32` | Да | Рост CPU, памяти, World и network work |
| `MaxScheduledPhantomProfiles` | int | Ёмкость Scheduler для durable profiles | `100..10000`, не меньше materialized cap | Да | Слишком большое значение увеличивает память и очереди |
| `PhantomSchedulerPulseMillis` | int, мс | Период Scheduler pulse | `50..250`, preset `100` | Да | Меньше — больше CPU; больше — медленнее реакция |
| `PhantomSchedulerProfilesPerPulse` | int | Сколько profiles Scheduler рассматривает за pulse | `4..64`, preset `16` | Да | Большое значение создаёт burst CPU |
| `PhantomPopulationTarget` | int | Целевое число durable Phantom identities | `0..500` для обычной локальной игры, preset `10` | Да | Создание аккаунтов/персонажей и рост DB |
| `PhantomPopulationActiveTarget` | int | Целевое число ACTIVE profiles | `0..min(population, materialized)`, preset `5` | Да | Главный прямой рычаг текущей игровой нагрузки |
| `PhantomPopulationCreationInFlight` | int | Параллельный лимит creation saga | `1..4`, preset `2` | Да | Большое значение усиливает DB/initialization burst |
| `PhantomPopulationBoundariesPerPulse` | int | Лимит population boundary/reconcile work за pulse | `16..128`, preset `64` | Да | Слишком мало замедляет bootstrap, слишком много создаёт burst |
| `PhantomPartyOperationsPerPulse` | int | Лимит party operations за pulse | `32..256`, preset `64` | Да | Рост contention и групповой нагрузки |
| `PhantomSocialCacheProfiles` | int | Максимум profiles в social cache | `256..2048`, preset `1024` | Да | Большое значение расходует память, малое повышает churn |
| `PhantomPopulationTimeZone` | IANA zone ID | Часовой пояс schedule templates | `UTC` или один проверенный локальный IANA ID | Да | Смена зоны сдвигает online schedule всей population |
| `EnablePhantomEcology` | boolean | Включает durable ecology assignment, causal catch-up и turnover | `False`; `True` только вместе с включённым Phantom runtime, local preset `True` | Да | При первом включении существующие managed profiles получают immutable ecology assignment |
| `PhantomEcologyPreset` | enum | Выбирает профиль виртуального возраста и распределения темпа/личности для новых назначений | `FRESH`, `LIVING` или `MATURE`; preset `LIVING` | Да | Не меняет уже committed assignment; проценты находятся в versioned XML |
| `PhantomEcologyWorldAgeDays` | int | Задаёт виртуальный возраст мира для новых назначений | `-1` для bounded default preset или `0..3650` | Да | Большой возраст создаёт более длинный bounded catch-up backlog |
| `PhantomEcologyArchiveLimit` | int | Ограничивает число durable `ARCHIVED` identities | `1..1000000`, preset `1000` | Да | После достижения cap turnover останавливается; история автоматически не удаляется |

Parser дополнительно обеспечивает жёсткие технические границы: `MaxMaterializedPhantoms=1..10000`, `MaxScheduledPhantomProfiles=1..1000000`, pulse `10..1000` мс, creation in-flight `1..64`, `PhantomEcologyWorldAgeDays=-1` или `0..3650`, `PhantomEcologyArchiveLimit=1..1000000`; `ACTIVE <= population <= scheduled` и `ACTIVE <= materialized`. Эти пределы означают «валидно», а не «безопасно для конкретного компьютера».

## Что можно крутить для количества ботов

Меняйте сначала `PhantomPopulationTarget`, затем согласуйте `PhantomPopulationActiveTarget`. Durable target определяет число профилей/аккаунтов/персонажей в базе, ACTIVE target — сколько из них одновременно требуют наиболее подробной симуляции. `MaxMaterializedPhantoms` должен быть не ниже ACTIVE target, но его не надо автоматически приравнивать ко всей population.

После изменения файла нужен restart GameServer: `//phantom enable` не перечитывает конфиг. Перед увеличением target проверьте `//phantom status`; поднимайте значения ступенчато.

## Что относится только к производительности

`MaxScheduledPhantomProfiles`, `PhantomSchedulerPulseMillis`, `PhantomSchedulerProfilesPerPulse`, `PhantomPopulationCreationInFlight`, `PhantomPopulationBoundariesPerPulse`, `PhantomPartyOperationsPerPulse` и `PhantomSocialCacheProfiles` задают бюджеты/ёмкости. Они не делают поведение умнее, не задают скорость прокачки и не меняют personality.

## Что не надо крутить без причины

- Не ставьте `PhantomPopulationActiveTarget` выше materialization cap.
- Не уменьшайте pulse одновременно с ростом work-per-pulse: это умножает нагрузку.
- Не поднимайте creation in-flight ради уже созданной population.
- Не используйте parser maximum как local-play recommendation.
- Не включайте diagnostics постоянно без необходимости.
- Не редактируйте ownership tokens, reserved accounts и `population.state` вручную.

## Безопасный reset/reseed

Сначала запросите read-only preview:

```text
//phantom reset preview
```

Команда показывает exact counts удаления/detach, сохраняемые world/history effects, blockers, snapshot hash и одноразовый token со сроком 120 секунд. Если есть blocker, token не выдаётся.

Подтвердите один раз:

```text
//phantom reset confirm <TOKEN>
//phantom reset confirm <TOKEN> reseed
```

Первый вариант оставляет population пустой. Второй после успешного reset запускает существующий PopulationManager с уже загруженным конфигом. `CONFIG_DISABLED` означает: reset завершён, но reseed не запускался. Отменить token:

```text
//phantom reset cancel
```

Reset не откатывает завершённые сделки, human-owned items, mail/history и другие законные world effects. Контакты/дружба с удаляемым Phantom безопасно detach. Неоднозначная clan/auction/wedding/cursed-weapon и другая shared ownership блокирует всю операцию до mutation.

## Настройки живой экологии

Реализация **Goal033 — Living population ecology** хранит assignment в durable `population.ecology`. Уровень human player не участвует ни в выборе возраста, ни в pace, ни в historical target. Preset влияет только на новые назначения; restart и повторное чтение XML не перебрасывают уже committed profile между cohorts.

`FRESH`, `LIVING` и `MATURE` задают bounded виртуальный возраст и распределения `CASUAL`/`REGULAR`/`FAST`/`OUTLIER`, personality и schedule templates. Точные проценты и лимиты catch-up находятся в `dist/game/data/phantoms/population/high-five-ecology-v1.xml`, а не дублируются parser-ключами. Pace определяет долю доступного времени в schedule-aware 15-минутных блоках; это не XP/reward multiplier. Catch-up исполняется только через causal Goal033A Background lifecycle.

Turnover переводит только безопасный managed profile в durable `ARCHIVED`, исключает его из population target и создаёт replacement через существующий PopulationManager. Автоматического удаления identity/history нет. `PhantomEcologyArchiveLimit` останавливает дальнейший turnover при cap. Для полного controlled reset/reseed используйте двухфазную команду из раздела выше; она создаёт новые assignments из текущего preset.

`//phantom status` показывает preset/hash, managed/archived/pending counts, pace/personality/schedule распределения, catch-up counters и read-only live level histogram. Personality traits фиксируются при первом создании Social state и затем не reroll. Class-transfer automation остаётся отложенной до Goal036.

Goal033 имеет статус `SUCCESS`: guarded production-composed LIVING 10/5, restart и Goal032 reset/reseed regressions прошли на выделенной `l2jmobiush5_phantom_test`, созданные тестовые профили очищены. Production `l2jmobiush5` automated gates не подключают, не изменяют и не очищают.
