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

Parser дополнительно обеспечивает жёсткие технические границы: `MaxMaterializedPhantoms=1..10000`, `MaxScheduledPhantomProfiles=1..1000000`, pulse `10..1000` мс, creation in-flight `1..64`; `ACTIVE <= population <= scheduled` и `ACTIVE <= materialized`. Эти пределы означают «валидно», а не «безопасно для конкретного компьютера».

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

## Каких gameplay-настроек пока нет

Сейчас нет parser-ключей для:

- распределения уровней и устойчивых low/mid/high cohorts;
- slow/normal/fast/outlier progression pace;
- дат/фаз появления новых новичков;
- personality/archetype percentages;
- fresh/living/mature gameplay presets;
- независимой ecology, привязанной к возрасту мира, а не к уровню human player.

Эти gameplay knobs принадлежат **Goal033 — Living population ecology**. Не имитируйте их изменением Scheduler budget keys.
