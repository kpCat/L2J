# LIVE-003C — creation throughput и durable 5000

**Статус: GREEN.**

## Причина и точечное исправление

В исходном `PhantomPopulationDecision` первый успешный `PROGRESSED` возвращал `RETRY(25 ms)`. DecisionEngine ждал новую WARM delivery, а её штатный cadence равен 1000 ms. Четыре последовательных durable вызова `advanceCreation()` поэтому расходовали несколько WARM ticks; ранее измеренный темп составлял 30.99 профиля/мин при `CreationInFlight=2`.

DB-free RED через зарегистрированный `population.create_character` handler и `MemoryStore` подтвердил это: одна WARM delivery вернула `RETRY` вместо `SUCCESS` (`0/1`, seed 16001601). После исправления handler продолжает только успешные `PROGRESSED` с новым component row version, максимум четыре вызова. Перед каждым следующим вызовом проверяется cancellation; `RETRY`, `READY`, `INCONSISTENT`, `NOT_PENDING` завершают handler немедленно. Durable store, schema, scheduler cadence, CreationInFlight и private budgets не менялись.

Production-файл: `java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationDecision.java`. Тестовый маршрут добавлен в `PhantomPopulationSuite`, `PhantomPopulationTestDoubles`, `PhantomTestLauncher` и `build.xml`.

## Code gates и доставка

- Новый focused target: GREEN 3/3; restart/fault creation, reconciliation, lifecycle, Goal033 ecology и Goal033A historical Ant targets завершились с exit 0. `git diff --check` чистый.
- Code commit/push: `a4bb55270d33bfdd0ce96e0367dd509b5c3358b2`, ветка `feature/phantom-world`.
- Один `ant jar` из clean detached checkout exact code SHA: exit 0. Private GameServer.jar SHA-256 `9FD64574CADF7628110684E79A457E1D88462B23F0FFA50AC625275147A0636F`. LoginServer.jar, manifest и private configs сохранили прежние hashes; старый GameServer.jar сохранён в private backup.

## PLAY ramp

Read-only baseline перед запуском: 2668 managed / 2666 linked, unique names/accounts 2666/2666, duplicates 0, background positions 2632. LocalPlay был STOPPED, порты 2106/9014/7777 закрыты. Штатный `Start-LocalPlay.ps1 -Background` запустил owned Login PID 22380 и Game PID 17964 в `20:05:53.6531610+03:00`.

Gate 5000/5000 достигнут в `20:17:23.5151358+03:00`, через 11.4977 минуты от старта: 2332 новых managed, средний rate 202.82 профиля/мин. Имена и аккаунты уникальны 5000/5000, дубликатов 0. Background committed states выросли с 2632 до 4645, ecology components — до 5000. На ramp heap peak 93.85%, не больше одного подряд sample >90%; threads 160–166, DB connections 13–15 из 151, fatal markers 0.

Подробные SELECT/SHOW и safety samples: `.phantom-local/logs/LIVE-003C-SCALE-5000-THROUGHPUT/`; компактный ряд — `LIVE003C_RUNTIME_5000.tsv`.

## 15-минутный soak и завершение

Soak шёл с `20:17:23.5151358` до `20:32:26.4548589+03:00`, после подтверждённого linked=5000. На всех samples сохранялись 5000/5000, unique names/accounts 5000/5000, duplicates/fatal 0. В 30 ramp+soak samples heap находился в пределах 80.71–93.85% от 4096 MiB, максимум два подряд >90%; Game threads 160–166, DB connections 13–15 из 151. В окне soak обновились 3002 background.state, 3058 background.catchup и 2709 ecology rows. Post-soak committed background positions — 4978/5000; оставшиеся 22 не объявлены завершёнными.

Штатный `Stop-LocalPlay.ps1` остановил только owned Game PID 17964 и Login PID 22380; `Check-LocalPlay.ps1` подтвердил STOPPED, staleRecord=False и закрытые 2106/9014/7777. Итоговый post-stop PLAY snapshot: 5000 managed / 5000 linked, 5000 уникальных имён и аккаунтов, duplicates 0, background positions 4978. Все профили сохранены, private PopulationTarget остался 5000. Конфиги, manifest, LoginServer.jar и budgets сохранили baseline hashes/values; PLAY direct DML/DDL не применялись.

Изменения ограничены указанными пятью code/test/build files и четырьмя task outputs. Другие хроники, schema, generator-library и private budgets не менялись. Runtime 10000 и LIVE-004/005 не начинались. Подробные команды и hashes приведены в `EVIDENCE.md`; report commit SHA будет указан в итоговом ответе после commit/push.
