# PHANTOM-AUTONOMOUS-PARTY-DUNGEONS-016

26 ноября 2026, Europe/Chisinau. **CODE_READY / DEPLOYED** на Bartz. **LIVE_NOT_OBSERVED** для новых Party и SSQ.

## Изменения

- Логический поиск TASK130 использует прежние проверки уровня, общего диапазона NPC, ролей и доступности. Среди допустимых кандидатов приоритет учитывает маршрут, недостающие роли, personality, pace и черты. Сохранены проценты 0/20/35/50/80, native consent, durable SOLO↔PARTY, человеческие приглашения и PreferHumanFollow.
- Для SSQ отобраны первые направленные связи LIVE-002. Статическая сверка нашла 17 связей с совпавшими штатными данными. Конкретная цель допускается только при подходящих уровне, готовности, доступе и паре живых Ziggurat. Подход использует навигацию 013. Штатный bypass с индексом 0 повторно проверяет право входа. Фактическая посадка сохраняется как промежуточный checkpoint, затем требуется отдельное прибытие к споту. Для SSQ каждый участник проходит эту цепочку до формирования native Party.
- Выход делегирован парному Ziggurat, после посадки маршрут продолжается от реального положения. Отказ, закрытая дверь, нехватка маршрута или изменение доступа возвращают выбор к действующим путям перепланирования. Подземные награды идут через штатный бой. Остальные связи закрытого корпуса, дальние подходы за пределами допуска и неподтверждённая геометрия ожидают отдельного подтверждения.
- В загруженных настройках Bartz `AltOpenCatacumbs=True` и `AltOpenNecropolis=True`. Когда конфигурация требует членства или выигравшей печати, используется штатный предикат Seven Signs с существующим Personal QoL. Незарегистрированный кандидат при отказе выбирает другое занятие. Автоматическая регистрация через Priest не потребовалась. Rift readiness сохранена.

## Проверки и поставка

- JDK 25 Ant `jar compile-tests` и итоговый `jar` завершились с exit 0. Owner profile 38/38, DungeonGatekeeperAccessChecks 6/6, directed cross-region013 PASS, party-route 9/9. Сборка выполнялась вне рабочего dist.
- До остановки пассивный census дал 1745 managed, 106 ordinary с допуском, 2 scouts, 200 лавок, 0 людей. Агрегат Bartz 33331 дал максимум уровня 13, 0 связанных online level 40+ и 0 связанных online SSQ. Профиль 20 ранее в этой сессии получил +1048 EXP/+80 SP.
- Первый STOP в прежней сборке отложен после истечения времени drain с 18 retained. Повторный штатный STOP завершился с exit 0. Перед заменой JAR подтверждены 0 `online=2` и 0 `background.owned-store`. Установлен только GameServer JAR и обновлён BUILD_INFO.
- Источник `172edc0703233164068ae055a48c678e07d51d27`. SHA-256 установленного JAR `C7914B0CDF032F012B97AA27457A094B6B681670F1DE6296AFB9BBB7095E3483`. Mobius GameServer PID 6104 на 7777 и Mobius LoginServer PID 20168 на 2106/9014 в интерактивной сессии 1. MariaDB Bartz 33331 сохранила PID 28664.
- После запуска первый census дал 1745 managed, 2 ordinary с допуском, 9 scouts, 200 лавок, 0 людей. Наблюдались нативное движение профиля 105 и восстановление профиля 35. Новых наград в этом срезе нет. Цели 5000/128/160/32, heap 12 GiB, 166 learned TSV, Geo203, рабочие конфиги, checkpoints и защита профиля 7 сохранены. TEST и MAIN3308 в операции не использовались.

## Scope и уборка

- Точка возврата `D:\Backups\PhantomWorld\PHANTOM-AUTONOMOUS-PARTY-DUNGEONS-016` содержит прежние GameServer.jar и BUILD_INFO, контрольные суммы и инструкцию. Сохранена для отката бинарников с текущими данными.
- Принадлежащий задаче `D:\Temp\Codex\PhantomWorld\PHANTOM-AUTONOMOUS-PARTY-DUNGEONS-016\r01` удалён после завершения сборки. Удалено 178175898 байт. Временных процессов и файлов задачи не осталось. Bartz оставлен работающим.
- Scope включает 12 Java-файлов и этот отчёт. Для baseline и сборки выполнены `git symbolic-ref --short HEAD`, `git rev-parse HEAD`, `git rev-parse origin/feature/phantom-world`, `git ls-remote --exit-code origin refs/heads/feature/phantom-world`, `git status --short` и его адресный вариант, `git archive --format=tar --output=<scratch>/source.tar <baseline> -- <точные входы Ant>`. Scope проверен через `git diff --stat -- <scope>`, `git diff --cached --quiet` и `git -c core.whitespace=cr-at-eol diff --check` с точными путями. Для записи использованы `git add -- <новые файлы>`, два `git commit --only -m <сообщение> -- <точные пути>` и обычный `git push origin HEAD:refs/heads/feature/phantom-world`. Публикация сверяется отдельно.
- Mojibake-маркеры в изменённых файлах проверены.
- Escaped Cyrillic в изменённых файлах проверены.
